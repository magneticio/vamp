package io.vamp.container_driver.marathon

import io.vamp.common.akka.ActorExecutionContextProvider
import io.vamp.common.config.Config
import io.vamp.common.crypto.Hash
import io.vamp.common.http.HttpClient
import io.vamp.common.spi.ClassMapper
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver._
import io.vamp.container_driver.notification.{ UndefinedMarathonApplication, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import org.json4s._

import scala.concurrent.Future

class MarathonDriverActorMapper extends ClassMapper {
  val name = "marathon"
  val clazz = classOf[MarathonDriverActor]
}

object MarathonDriverActor {

  private val config = "vamp.container-driver"

  val mesosUrl = Config.string(s"$config.mesos.url")
  val marathonUrl = Config.string(s"$config.marathon.url")

  val apiUser = Config.string(s"$config.marathon.user")
  val apiPassword = Config.string(s"$config.marathon.password")

  val sse = Config.boolean(s"$config.marathon.sse")
  val expirationPeriod = Config.duration(s"$config.marathon.expiration-period")
  val reconciliationPeriod = Config.duration(s"$config.marathon.reconciliation-period")

  val workflowNamePrefix = Config.string(s"$config.marathon.workflow-name-prefix")

  object Schema extends Enumeration {
    val Docker, Cmd, Command = Value
  }

  MarathonDriverActor.Schema.values
}

case class MesosInfo(frameworks: Any, slaves: Any)

case class MarathonDriverInfo(mesos: MesosInfo, marathon: Any)

class MarathonDriverActor extends ContainerDriverActor with ContainerBuffer with MarathonSse with ActorExecutionContextProvider with ContainerDriver {

  import ContainerDriverActor._

  protected val expirationPeriod = MarathonDriverActor.expirationPeriod()

  protected val reconciliationPeriod = MarathonDriverActor.reconciliationPeriod()

  private val url = MarathonDriverActor.marathonUrl()

  private val workflowNamePrefix = MarathonDriverActor.workflowNamePrefix()

  private implicit val formats: Formats = DefaultFormats

  private val headers: List[(String, String)] = HttpClient.basicAuthorization(MarathonDriverActor.apiUser(), MarathonDriverActor.apiPassword())

  protected val nameDelimiter = "/"

  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def appId(workflow: Workflow): String = s"$workflowNamePrefix${artifactName2Id(workflow)}"

  protected def appId(deployment: Deployment, breed: Breed): String = s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  override protected def supportedDeployableTypes = DockerDeployableType :: CommandDeployableType :: Nil

  override def receive = super[ContainerBuffer].receive orElse {

    case InfoRequest                ⇒ reply(info)

    case r: ReconcileService        ⇒ reconcile(r.deployment, r.service)

    case r: ReconcileWorkflow       ⇒ reconcile(r.workflow)

    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))

    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = if (MarathonDriverActor.sse()) openEventStream(url)

  private def info: Future[Any] = {

    def remove(key: String): Any ⇒ Any = {
      case m: Map[_, _] ⇒ m.asInstanceOf[Map[String, _]].filterNot { case (k, v) ⇒ k == key } map { case (k, v) ⇒ k → remove(key)(v) }
      case l: List[_]   ⇒ l.map(remove(key))
      case any          ⇒ any
    }

    for {
      slaves ← httpClient.get[Any](s"${MarathonDriverActor.mesosUrl()}/master/slaves")
      frameworks ← httpClient.get[Any](s"${MarathonDriverActor.mesosUrl()}/master/frameworks")
      marathon ← httpClient.get[Any](s"$url/v2/info", headers)
    } yield {

      val s: Any = slaves match {
        case s: Map[_, _] ⇒ s.asInstanceOf[Map[String, _]].getOrElse("slaves", Nil)
        case _            ⇒ Nil
      }

      val f = (remove("tasks") andThen remove("completed_tasks"))(frameworks)

      ContainerInfo("marathon", MarathonDriverInfo(MesosInfo(f, s), marathon))
    }
  }

  override protected def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {

    validateDeployable(service.breed.deployable)

    val id = appId(deployment, service.breed)
    val name = s"${deployment.name} / ${service.breed.deployable.definition}"
    if (update) log.info(s"marathon update service: $name") else log.info(s"marathon create service: $name")

    val app = MarathonApp(
      id,
      container(deployment, cluster, service),
      service.scale.get.instances,
      service.scale.get.cpu.value,
      Math.round(service.scale.get.memory.value).toInt,
      environment(deployment, cluster, service),
      cmd(deployment, cluster, service),
      labels = labels(deployment, cluster, service)
    )

    sendRequest(update, id, requestPayload(deployment, cluster, service, purge(app)))
  }

  override protected def deploy(workflow: Workflow, update: Boolean): Future[Any] = {

    validateDeployable(workflow.breed.asInstanceOf[DefaultBreed].deployable)

    val id = appId(workflow)
    if (update) log.info(s"marathon update workflow: ${workflow.name}") else log.info(s"marathon create workflow: ${workflow.name}")
    val scale = workflow.scale.get.asInstanceOf[DefaultScale]

    val marathonApp = MarathonApp(
      id,
      container(workflow),
      scale.instances,
      scale.cpu.value,
      Math.round(scale.memory.value).toInt,
      environment(workflow),
      cmd(workflow),
      labels = labels(workflow)
    )

    sendRequest(update, id, Extraction.decompose(purge(marathonApp)))
  }

  private def purge(app: MarathonApp): MarathonApp = {
    // workaround - empty args may cause Marathon to reject the request, so removing args altogether
    if (app.args.isEmpty) app.copy(args = null) else app
  }

  private def sendRequest(update: Boolean, id: String, payload: JValue) = {
    if (update) {
      httpClient.get[Any](s"$url/v2/apps/$id", headers).flatMap { response ⇒
        val changed = Extraction.decompose(response).children.headOption match {
          case Some(app) ⇒ app.diff(payload).changed
          case None      ⇒ payload
        }
        if (changed != JNothing) httpClient.put[Any](s"$url/v2/apps/$id", changed, headers) else Future.successful(false)
      }
    } else {
      httpClient.post[Any](s"$url/v2/apps", payload, headers, logError = false).recover {
        case t if t.getMessage != null && t.getMessage.contains("already exists") ⇒ // ignore, sync issue
        case t ⇒
          log.error(t, t.getMessage)
          Future.failed(t)
      }
    }
  }

  private def container(workflow: Workflow): Option[Container] = {
    if (DockerDeployableType.matches(workflow.breed.asInstanceOf[DefaultBreed].deployable)) Some(Container(docker(workflow))) else None
  }

  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = {
    if (DockerDeployableType.matches(service.breed.deployable)) Some(Container(docker(deployment, cluster, service))) else None
  }

  private def cmd(workflow: Workflow): Option[String] = {
    if (CommandDeployableType.matches(workflow.breed.asInstanceOf[DefaultBreed].deployable)) Some(workflow.breed.asInstanceOf[DefaultBreed].deployable.definition) else None
  }

  private def cmd(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[String] = {
    if (CommandDeployableType.matches(service.breed.deployable)) Some(service.breed.deployable.definition) else None
  }

  private def requestPayload(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, app: MarathonApp): JValue = {
    val (local, dialect) = (cluster.dialects.get(Dialect.Marathon), service.dialects.get(Dialect.Marathon)) match {
      case (_, Some(d))    ⇒ Some(service) → d
      case (Some(d), None) ⇒ None → d
      case _               ⇒ None → Map()
    }

    (app.container, app.cmd, dialect) match {
      case (None, None, map: Map[_, _]) if map.asInstanceOf[Map[String, _]].get("cmd").nonEmpty ⇒
      case (None, None, _) ⇒ throwException(UndefinedMarathonApplication)
      case _ ⇒
    }

    Extraction.decompose(interpolate(deployment, local, dialect)) merge Extraction.decompose(app)
  }

  override protected def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    log.info(s"marathon delete app: $id")
    httpClient.delete(s"$url/v2/apps/$id", headers, logError = false) recover { case _ ⇒ None }
  }

  override protected def undeploy(workflow: Workflow) = {
    val id = appId(workflow)
    log.info(s"marathon delete workflow: ${workflow.name}")
    httpClient.delete(s"$url/v2/apps/$id", headers, logError = false) recover { case _ ⇒ None }
  }

  private def reconcile(deployment: Deployment, service: DeploymentService): Unit = {
    log.debug(s"marathon reconcile: ${deployment.name} / ${service.breed.name}")
    reconcile(appId(deployment, service.breed)).foreach {
      case Some(containers) ⇒ self ! ContainerService(deployment, service, Option(containers))
      case _                ⇒ self ! ContainerService(deployment, service, None)
    }
  }

  private def reconcile(workflow: Workflow): Unit = {
    log.debug(s"marathon reconcile workflow: ${workflow.name}")
    reconcile(appId(workflow)).foreach {
      case Some(containers) ⇒ self ! ContainerWorkflow(workflow, Option(containers))
      case _                ⇒ self ! ContainerWorkflow(workflow, None)
    }
  }

  private def reconcile(id: String): Future[Option[Containers]] = {
    httpClient.get[AppsResponse](s"$url/v2/apps?id=$id&embed=apps.tasks", headers, logError = false) recover { case _ ⇒ None } map {
      case apps: AppsResponse ⇒ apps.apps.find(app ⇒ app.id == id).map(containers)
      case _                  ⇒ None
    }
  }

  private def containers(app: App): Containers = {
    val scale = DefaultScale("", Quantity(app.cpus), MegaByte(app.mem), app.instances)
    val instances = app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined))
    Containers(scale, instances)
  }

  protected def artifactName2Id(artifact: Artifact): String = {

    val id = artifact.name match {
      case idMatcher(_*) ⇒ artifact.name
      case _             ⇒ Hash.hexSha1(artifact.name).substring(0, 20)
    }

    artifact match {
      case breed: Breed if breed.name != id ⇒ if (breed.name.matches("^[\\d\\p{L}].*$")) s"${breed.name.replaceAll("[^\\p{L}\\d]", "-")}-$id" else id
      case _                                ⇒ id
    }
  }
}
