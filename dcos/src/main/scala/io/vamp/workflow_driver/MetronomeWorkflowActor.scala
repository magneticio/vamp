package io.vamp.workflow_driver

import akka.pattern.ask
import io.vamp.common.akka.IoC
import io.vamp.common.{ ClassMapper, Config }
import io.vamp.common.http.{ HttpClient, HttpClientException }
import io.vamp.container_driver.{ ContainerDriverValidation, DeployableType, Docker, DockerDeployableType }
import io.vamp.model.artifact._
import io.vamp.persistence.PersistenceActor
import io.vamp.pulse.Percolator.GetPercolator
import io.vamp.pulse.PulseActor
import scala.concurrent.Future
import scala.util.Try
import cats.implicits.catsStdInstancesForList
import cats.implicits.toTraverseOps
import cats.implicits.catsStdInstancesForFuture

class MetronomeWorkflowActorMapper extends ClassMapper {
  val name = "metronome"
  val clazz = classOf[MetronomeWorkflowActor]
}

// TODO: Change http to typed api such as http.get[Type] instead of http.get[any] etc..
class MetronomeWorkflowActor extends WorkflowDriver with ContainerDriverValidation {

  private val metronomeUrl = Config.string("vamp.workflow-driver.metronome.url")()

  private val httpClient = new HttpClient

  override protected def supportedDeployableTypes: List[DeployableType] = List(DockerDeployableType)

  override protected def info: Future[Map[_, _]] = httpClient.get[Any](s"$metronomeUrl/v1/jobs").map {
    _ ⇒ Map("metronome" → Map("url" → metronomeUrl))
  }

  override protected def request(workflows: List[Workflow]): Unit = {
    val timeScheduled = workflows.filter(_.schedule.isInstanceOf[TimeSchedule])
    if (timeScheduled.nonEmpty) requestTimeScheduled(timeScheduled)

    val eventScheduled = workflows.filter(_.schedule.isInstanceOf[EventSchedule])
    if (eventScheduled.nonEmpty) requestEventScheduled(eventScheduled)
  }

  private def requestTimeScheduled(workflows: List[Workflow]): Unit =
    allExistingJobsNames.map { existingJobName ⇒
      workflows.foreach { workflow ⇒
        IoC.actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowInstances(
          workflow,
          if (existingJobName.contains(getWorkflowId(workflow))) List(instance(workflow)) else List())
      }
    }

  private def requestEventScheduled(workflows: List[Workflow]): Unit =
    allExistingJobsNames.map { existingJobNames ⇒
      workflows.foreach { workflow ⇒
        IoC.actorFor[PulseActor] ? GetPercolator(WorkflowDriverActor.percolator(workflow)) map {
          case Some(_) if runnable(workflow) ⇒ IoC.actorFor[PersistenceActor] ! PersistenceActor
            .UpdateWorkflowInstances(workflow, List(instance(workflow)))
          case _ ⇒
            IoC.actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowInstances(workflow, List())
            if (existingJobNames.contains(getWorkflowId(workflow))) safelyDeleteWorkflow(workflow)
        }
      }
    }

  private def instance(workflow: Workflow): Instance = Instance(workflow.name, "", Map(), deployed = true)

  override protected def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case w if w.schedule != DaemonSchedule ⇒ enrich(w, data).flatMap { workflow ⇒
      validateDeployable(workflow.breed.asInstanceOf[DefaultBreed].deployable)

      workflow.schedule match {
        case TimeSchedule(period, repeat, start) ⇒
          for {
            workflowId ← createJobForWorkflowIfNecessary(workflow)
            cronScheduleId = s"${workflowId}cronschedule"
            allSchedules ← allExistingScheduleNames(workflowId)
            _ ← allSchedules.traverse[Future, Any] { scheduleId ⇒
              httpClient.delete(s"$metronomeUrl/v1/jobs/$workflowId/schedules/$scheduleId")
            }
            _ ← httpClient.post[Any](
              s"$metronomeUrl/v1/jobs/$workflowId/schedules",
              cronScheduleAsJson(cronScheduleId, period))
          } yield ()
        case EventSchedule(tags) ⇒
          for {
            workflowId ← createJobForWorkflowIfNecessary(workflow)
            _ ← httpClient.post[Any](s"$metronomeUrl/v1/jobs/$workflowId/runs", "{}")
          } yield ()
        case DaemonSchedule ⇒
          Future.successful(())
      }
    }
  }

  // TODO: What if it the schedule is a DeamonSchedule?
  override protected def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case w: Workflow if (w.schedule != DaemonSchedule) ⇒ safelyDeleteWorkflow(w)
  }

  private def safelyDeleteWorkflow(workflow: Workflow): Future[Unit] = {
    val workflowId = getWorkflowId(workflow)
    for {
      allExistingWorkflows ← allExistingJobsNames
      _ ← if (allExistingWorkflows.contains(workflowId)) {
        for {
          allSchedules ← allExistingScheduleNames(workflowId)
          _ ← allSchedules.traverse[Future, Any] { scheduleId ⇒
            httpClient.delete(s"$metronomeUrl/v1/jobs/$workflowId/schedules/$scheduleId")
          }
          _ ← httpClient.delete(s"$metronomeUrl/v1/jobs/$workflowId")
        } yield ()
      }
      else Future.successful(()) // Do nothing. The job is not registered
    } yield ()
  }

  private def allExistingJobsNames: Future[List[String]] = httpClient.get[Any](s"$metronomeUrl/v1/jobs") map {
    case list: List[_] ⇒ list.map(_.asInstanceOf[Map[String, String]].getOrElse("id", "")).filter(_.nonEmpty)
    case _             ⇒ List()
  }

  private def allExistingScheduleNames(workflowId: String): Future[List[String]] = httpClient.get[Any](
    s"$metronomeUrl/v1/jobs/$workflowId/schedules") map {
      case list: List[_] ⇒ list.map(_.asInstanceOf[Map[String, String]].getOrElse("id", "")).filter(_.nonEmpty)
      case _             ⇒ List()
    }

  private def getWorkflowId(workflow: Workflow): String = workflow.name.toLowerCase.filter(c ⇒ c.isLetter || c.isDigit)

  /*
    * This function creates a job in metronome for a workflow.
    * It first checks that there is not already an existing instance of this job, identical with what should be newly created.
    */
  private def createJobForWorkflowIfNecessary(workflow: Workflow): Future[String] = {
    val workflowId = getWorkflowId(workflow)
    val environmentVariablesAsString = workflow
      .breed
      .asInstanceOf[DefaultBreed]
      .environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) → ev.interpolated.getOrElse(""))
      .map { case (n, v) ⇒ s"""-e $n=$v""" }
      .mkString(" ")

    val usedNetworkName = {
      val networkName = workflow.network.getOrElse(Docker.network())
      if (networkName == "BRIDGE") "bridge" else networkName
    }

    val commandToRun = s"docker container run $environmentVariablesAsString --network=${usedNetworkName} " +
      s"--rm ${workflow.breed.asInstanceOf[DefaultBreed].deployable.definition}"

    val workflowAsJson = jobAsJson(workflowDescription = workflow.name, workflowId = workflowId,
      cpuQuantity = workflow.scale.get.asInstanceOf[DefaultScale].cpu.value,
      memoryQuantity = workflow.scale.get.asInstanceOf[DefaultScale].memory.value, cmd = commandToRun
    )

    for {
      existingJsonForThisName ← httpClient.get[Any](s"$metronomeUrl/v1/jobs/${workflowId}").map { x ⇒
        val jsonAsMap = x.asInstanceOf[Map[String, Any]]
        val runAsMap = fromJsonMap(jsonAsMap, "run", Map.empty[String, Any])
        Some(jobAsJson(
          workflowDescription = fromJsonMap(jsonAsMap, "description", ""),
          workflowId = fromJsonMap(jsonAsMap, "id", ""),
          cpuQuantity = fromJsonMap[Double](runAsMap, "cpus", -1),
          memoryQuantity = fromJsonMap[Double](runAsMap, "mem", -1),
          cmd = fromJsonMap(runAsMap, "cmd", "")
        ))
      }.recover { case e: HttpClientException ⇒ None }

      _ ← existingJsonForThisName match {
        case None                             ⇒ httpClient.post[Any](s"$metronomeUrl/v1/jobs", workflowAsJson)
        case Some(t) if (t == workflowAsJson) ⇒ Future.successful(())
        case Some(_) ⇒
          safelyDeleteWorkflow(workflow)
            .flatMap(_ ⇒ httpClient.post[Any](s"$metronomeUrl/v1/jobs", workflowAsJson))
      }
    } yield workflowId
  }

  def fromJsonMap[A](map: Map[String, Any], key: String, default: A): A = map
    .get(key)
    .flatMap(v ⇒ Try(v.asInstanceOf[A]).toOption)
    .getOrElse(default)

  // TODO Why not a case class?
  private def jobAsJson(workflowDescription: String, workflowId: String, cpuQuantity: Double, memoryQuantity: Double, cmd: String) = {
    s"""
       |{
       |  "description": "${workflowDescription}",
       |  "id": "$workflowId",
       |  "run":{
       |    "cpus": $cpuQuantity,
       |    "mem": $memoryQuantity,
       |    "disk": 128,
       |    "maxLaunchDelay": 1,
       |    "cmd": "$cmd"
       |  }
       |}
  """.stripMargin
  }

  // TODO Why not case class with constructor method that handles the transformation from asCronFormat?
  private def cronScheduleAsJson(cronScheduleId: String, repeatPeriod: TimeSchedule.RepeatPeriod) = {
    val hours =
      if (repeatPeriod.time.isDefined && repeatPeriod.time.get.toHours > 0)
        Some(repeatPeriod.time.get.toHours.toInt)
      else None

    val minutes =
      if (repeatPeriod.time.isDefined && repeatPeriod.time.get.toMinutes % 60 > 0)
        Some((repeatPeriod.time.get.toMinutes % 60).toInt)
      else None

    val asCronFormat = s"${minutes.map(m ⇒ s"*/$m").getOrElse("*")} ${hours.map(h ⇒ s"*/$h").getOrElse("*")} * * *"

    s"""
       |{
       |  "id": "$cronScheduleId",
       |  "cron": "$asCronFormat"
       |}
  """.stripMargin
  }

}
