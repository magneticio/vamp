package io.vamp.container_driver.marathon

import akka.actor.ActorRef
import io.vamp.common.{ ClassMapper, Config }
import io.vamp.common.akka.ActorExecutionContextProvider
import io.vamp.common.http.HttpClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver._
import io.vamp.container_driver.notification.{ UndefinedMarathonApplication, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import org.json4s.JsonAST.JObject
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

  val namespaceConstraint = Config.stringList(s"$config.marathon.namespace-constraint")

  object Schema extends Enumeration {
    val Docker, Cmd, Command = Value
  }

  MarathonDriverActor.Schema.values

  val dialect = "marathon"
}

case class MesosInfo(frameworks: Any, slaves: Any)

case class MarathonDriverInfo(mesos: MesosInfo, marathon: Any)

class MarathonDriverActor
    extends ContainerDriverActor
    with MarathonSse
    with MarathonNamespace
    with ActorExecutionContextProvider
    with ContainerDriver
    with HealthCheckMerger {

  import ContainerDriverActor._

  protected val expirationPeriod = MarathonDriverActor.expirationPeriod()

  protected val reconciliationPeriod = MarathonDriverActor.reconciliationPeriod()

  private val url = MarathonDriverActor.marathonUrl()

  private implicit val formats: Formats = DefaultFormats

  private val headers: List[(String, String)] = HttpClient.basicAuthorization(MarathonDriverActor.apiUser(), MarathonDriverActor.apiPassword())

  override protected def supportedDeployableTypes = DockerDeployableType :: CommandDeployableType :: Nil

  override def receive = {

    case InfoRequest                    ⇒ reply(info)

    case Get(services)                  ⇒ get(services)
    case d: Deploy                      ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                    ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways)     ⇒ reply(deployedGateways(gateways))

    case GetWorkflow(workflow, replyTo) ⇒ get(workflow, replyTo)
    case d: DeployWorkflow              ⇒ reply(deploy(d.workflow, d.update))
    case u: UndeployWorkflow            ⇒ reply(undeploy(u.workflow))

    case any                            ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = {

    def remove(key: String): Any ⇒ Any = {
      case m: Map[_, _] ⇒ m.asInstanceOf[Map[String, _]].filterNot { case (k, _) ⇒ k == key } map { case (k, v) ⇒ k → remove(key)(v) }
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

  private def get(deploymentServices: List[DeploymentServices]): Unit = {
    val replyTo = sender()
    deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).foreach {
      case (deployment, service) ⇒ get(appId(deployment, service.breed)).foreach {
        case Some(app) ⇒
          val (equalHealthChecks, health) = healthCheck(app, deployment.ports, service.healthChecks.getOrElse(List()))
          replyTo ! ContainerService(
            deployment,
            service,
            Option(containers(app)),
            health,
            equalHealthChecks = equalHealthChecks
          )
        case None ⇒ replyTo ! ContainerService(deployment, service, None, None)
      }
    }
  }

  private def get(workflow: Workflow, replyTo: ActorRef): Unit = {
    log.debug(s"marathon reconcile workflow: ${workflow.name}")
    get(appId(workflow)).foreach {
      case Some(app) ⇒
        val breed = workflow.breed.asInstanceOf[DefaultBreed]
        val (equalHealthChecks, health) = healthCheck(app, breed.ports, breed.healthChecks.getOrElse(List()))
        replyTo ! ContainerWorkflow(workflow, Option(containers(app)), health, equalHealthChecks)
      case _ ⇒ replyTo ! ContainerWorkflow(workflow, None)
    }
  }

  private def get(id: String): Future[Option[App]] = {
    httpClient.get[AppsResponse](s"$url/v2/apps?id=$id&embed=apps.tasks&embed=apps.taskStats", headers, logError = false) recover { case _ ⇒ None } map {
      case apps: AppsResponse ⇒ apps.apps.find(app ⇒ app.id == id)
      case _                  ⇒ None
    }
  }

  private def healthCheck(app: App, ports: List[Port], healthChecks: List[HealthCheck]): (Boolean, Option[Health]) = {
    val equalHealthChecks = MarathonHealthCheck.equalHealthChecks(ports, healthChecks, app.healthChecks)
    val newHealth = app.taskStats.map(ts ⇒ MarathonCounts.toServiceHealth(ts.totalSummary.stats.counts))
    equalHealthChecks → newHealth
  }

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {

    validateDeployable(service.breed.deployable)

    val id = appId(deployment, service.breed)
    val name = s"${deployment.name} / ${service.breed.deployable.definition}"
    if (update) log.info(s"marathon update service: $name") else log.info(s"marathon create service: $name")
    val constraints = (namespaceConstraint +: Nil).filter(_.nonEmpty)

    val app = MarathonApp(
      id,
      container(deployment, cluster, service),
      service.scale.get.instances,
      service.scale.get.cpu.value,
      Math.round(service.scale.get.memory.value).toInt,
      environment(deployment, cluster, service),
      cmd(deployment, cluster, service),
      healthChecks = retrieveHealthChecks(cluster, service).map(MarathonHealthCheck.apply(service.breed.ports, _)),
      labels = labels(deployment, cluster, service),
      constraints = constraints
    )

    sendRequest(update, id, requestPayload(deployment, cluster, service, purge(app)))
  }

  private def deploy(workflow: Workflow, update: Boolean): Future[Any] = {

    val breed = workflow.breed.asInstanceOf[DefaultBreed]

    validateDeployable(breed.deployable)

    val id = appId(workflow)
    if (update) log.info(s"marathon update workflow: ${workflow.name}") else log.info(s"marathon create workflow: ${workflow.name}")
    val scale = workflow.scale.get.asInstanceOf[DefaultScale]
    val constraints = (namespaceConstraint +: Nil).filter(_.nonEmpty)

    val marathonApp = MarathonApp(
      id,
      container(workflow),
      scale.instances,
      scale.cpu.value,
      Math.round(scale.memory.value).toInt,
      environment(workflow),
      cmd(workflow),
      labels = labels(workflow),
      healthChecks = retrieveHealthChecks(workflow).map(MarathonHealthCheck.apply(breed.ports, _)),
      constraints = constraints
    )

    sendRequest(update, id, requestPayload(workflow, purge(marathonApp)))
  }

  private def purge(app: MarathonApp): MarathonApp = {
    // workaround - empty args may cause Marathon to reject the request, so removing args altogether
    if (app.args.isEmpty) app.copy(args = null) else app
  }

  private def sendRequest(update: Boolean, id: String, payload: JValue) = {
    if (update) {
      httpClient.get[AppsResponse](s"$url/v2/apps?id=$id&embed=apps.tasks", headers).flatMap { appResponse ⇒
        val marathonApp = Extraction.extract[MarathonApp](payload)
        val changed = appResponse.apps.headOption.map(difference(marathonApp, _)).getOrElse(payload)

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

  /**
   * Checks the difference between a MarathonApp and an App to convert them two comparable objects (ComparableApp)
   * If healthCheck is changed it takes the latest array as values from:
   * @param marathonApp
   * If healthCheck deleted it needs to have an empty JSON Array as override value for the put request
   */
  private def difference(marathonApp: MarathonApp, app: App): JValue = {
    val comparableApp: ComparableApp = ComparableApp.fromApp(app)
    val comparableAppTwo: ComparableApp = ComparableApp.fromMarathonApp(marathonApp)
    val diff = Extraction.decompose(comparableApp).diff(Extraction.decompose(comparableAppTwo))

    diff.added.merge(diff.changed.mapField {
      case ("healthChecks", _) ⇒ "healthChecks" → JArray(marathonApp.healthChecks.map(Extraction.decompose))
      case xs                  ⇒ xs
    }).merge(diff.deleted.mapField {
      case ("healthChecks", _) ⇒ "healthChecks" → JArray(List())
      case xs                  ⇒ xs
    })
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
    val (local, dialect) = (deployment.dialects.get(MarathonDriverActor.dialect), cluster.dialects.get(MarathonDriverActor.dialect), service.dialects.get(MarathonDriverActor.dialect)) match {
      case (_, _, Some(d))       ⇒ Some(service) → d
      case (_, Some(d), None)    ⇒ None → d
      case (Some(d), None, None) ⇒ None → d
      case _                     ⇒ None → Map()
    }

    (app.container, app.cmd, dialect) match {
      case (None, None, map: Map[_, _]) if map.asInstanceOf[Map[String, _]].get("cmd").nonEmpty ⇒
      case (None, None, _) ⇒ throwException(UndefinedMarathonApplication)
      case _ ⇒
    }

    val base = Extraction.decompose(app) match {
      case JObject(l) ⇒ JObject(l.filter({ case (k, v) ⇒ k != "args" || v != JNull }))
      case other      ⇒ other
    }

    Extraction.decompose(interpolate(deployment, local, dialect)) merge base
  }

  private def requestPayload(workflow: Workflow, app: MarathonApp): JValue = {
    val dialect = workflow.dialects.getOrElse(MarathonDriverActor.dialect, Map())

    (app.container, app.cmd, dialect) match {
      case (None, None, map: Map[_, _]) if map.asInstanceOf[Map[String, _]].get("cmd").nonEmpty ⇒
      case (None, None, _) ⇒ throwException(UndefinedMarathonApplication)
      case _ ⇒
    }

    val base = Extraction.decompose(app) match {
      case JObject(l) ⇒ JObject(l.filter({ case (k, v) ⇒ k != "args" || v != JNull }))
      case other      ⇒ other
    }

    Extraction.decompose(interpolate(workflow, dialect)) merge base
  }

  private def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    log.info(s"marathon delete app: $id")
    httpClient.delete(s"$url/v2/apps/$id", headers, logError = false) recover { case _ ⇒ None }
  }

  private def undeploy(workflow: Workflow) = {
    val id = appId(workflow)
    log.info(s"marathon delete workflow: ${workflow.name}")
    httpClient.delete(s"$url/v2/apps/$id", headers, logError = false) recover { case _ ⇒ None }
  }

  private def containers(app: App): Containers = {
    val scale = DefaultScale(Quantity(app.cpus), MegaByte(app.mem), app.instances)
    val instances = app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined))
    Containers(scale, instances)
  }
}
