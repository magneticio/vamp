package io.vamp.container_driver.marathon

import akka.actor.{ Actor, ActorRef }
import io.vamp.common.akka.ActorExecutionContextProvider
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.NotificationErrorException
import io.vamp.common.vitals.InfoRequest
import io.vamp.common.{ ClassMapper, Config, ConfigMagnet }
import io.vamp.container_driver._
import io.vamp.container_driver.notification.{ UndefinedMarathonApplication, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact._
import io.vamp.model.notification.InvalidArgumentValueError
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.resolver.NamespaceValueResolver
import org.json4s.JsonAST.JObject
import org.json4s._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class MarathonDriverActorMapper extends ClassMapper {
  val name = "marathon"
  val clazz: Class[_] = classOf[MarathonDriverActor]
}

object MarathonDriverActor {

  private val config = "vamp.container-driver"
  private val marathonConfig = "vamp.container-driver.marathon"

  val mesosUrl: ConfigMagnet[String] = Config.string(s"$config.mesos.url")
  val marathonUrl: ConfigMagnet[String] = Config.string(s"$marathonConfig.url")

  val apiUser: ConfigMagnet[String] = Config.string(s"$marathonConfig.user")
  val apiPassword: ConfigMagnet[String] = Config.string(s"$marathonConfig.password")
  val apiToken: ConfigMagnet[String] = Config.string(s"$marathonConfig.token")

  val sse: ConfigMagnet[Boolean] = Config.boolean(s"$marathonConfig.sse")

  val readTimeToLivePeriod: ConfigMagnet[FiniteDuration] = Config.duration(s"$marathonConfig.cache.read-time-to-live")
  val writeTimeToLivePeriod: ConfigMagnet[FiniteDuration] = Config.duration(s"$marathonConfig.cache.write-time-to-live")

  val namespaceConstraint: ConfigMagnet[List[String]] = Config.stringList(s"$marathonConfig.namespace-constraint")

  val tenantIdOverride: ConfigMagnet[String] = Config.string(s"$marathonConfig.tenant-id-override")
  val tenantIdWorkflowOverride: ConfigMagnet[String] = Config.string(s"$marathonConfig.tenant-id-workflow-override")

  val useBreedNameForServiceName: ConfigMagnet[Boolean] = Config.boolean(s"$marathonConfig.use-breed-name-for-service-name")

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
    with MarathonNamespace
    with ActorExecutionContextProvider
    with ContainerDriver
    with HealthCheckMerger
    with ContainerDriverCache
    with NamespaceValueResolver {

  import ContainerDriverActor._

  lazy val tenantIdOverride: Option[String] = Try(Some(resolveWithNamespace(MarathonDriverActor.tenantIdOverride()))).getOrElse(None)

  lazy val tenantIdWorkflowOverride: Option[String] = Try(Some(resolveWithNamespace(MarathonDriverActor.tenantIdWorkflowOverride()))).getOrElse(None)

  lazy val useBreedNameForServiceName: Option[Boolean] = Try(Some(MarathonDriverActor.useBreedNameForServiceName())).getOrElse(None)

  lazy val readTimeToLivePeriod: FiniteDuration = MarathonDriverActor.readTimeToLivePeriod()
  lazy val writeTimeToLivePeriod: FiniteDuration = MarathonDriverActor.writeTimeToLivePeriod()

  private val url = MarathonDriverActor.marathonUrl()

  private implicit val formats: Formats = DefaultFormats

  private val headers: List[(String, String)] = {
    val token = MarathonDriverActor.apiToken()
    if (token.isEmpty)
      HttpClient.basicAuthorization(MarathonDriverActor.apiUser(), MarathonDriverActor.apiPassword())
    else
      List("Authorization" → s"token=$token")
  }

  override protected def supportedDeployableTypes: List[DeployableType] = DockerDeployableType :: CommandDeployableType :: Nil

  override def receive: Actor.Receive = {

    case InfoRequest                    ⇒ reply(info)

    case Get(services, equality)        ⇒ get(services, equality)
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
      slaves ← httpClient.get[Any](s"${MarathonDriverActor.mesosUrl()}/master/slaves", headers)
      frameworks ← httpClient.get[Any](s"${MarathonDriverActor.mesosUrl()}/master/frameworks", headers)
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

  private def get(deploymentServices: List[DeploymentServices], equalityRequest: ServiceEqualityRequest): Unit = {
    log.info("getting deployment services")
    val replyTo = sender()
    deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).foreach {
      case (deployment, service) ⇒ get(appId(deployment, service.breed)).foreach {
        case Some(app) ⇒
          val cluster = deployment.clusters.find { c ⇒ c.services.exists { s ⇒ s.breed.name == service.breed.name } }
          val equality = ServiceEqualityResponse(
            deployable = !equalityRequest.deployable || checkDeployable(service, app),
            ports = !equalityRequest.ports || checkPorts(deployment, cluster, service, app),
            environmentVariables = !equalityRequest.environmentVariables || checkEnvironmentVariables(deployment, cluster, service, app),
            health = !equalityRequest.health || checkHealth(deployment, service, app)
          )
          replyTo ! ContainerService(
            deployment,
            service,
            Option(containers(app)),
            app.taskStats.map(ts ⇒ MarathonCounts.toServiceHealth(ts.totalSummary.stats.counts)),
            equality = equality
          )
        case None ⇒ replyTo ! ContainerService(deployment, service, None, None)
      }
    }
  }

  private def checkDeployable(service: DeploymentService, app: App): Boolean = {
    if (CommandDeployableType.matches(service.breed.deployable))
      service.breed.deployable.definition == app.cmd.getOrElse("")
    else if (DockerDeployableType.matches(service.breed.deployable))
      service.breed.deployable.definition == app.container.flatMap(_.docker).map(_.image).getOrElse("")
    else true
  }

  private def checkPorts(deployment: Deployment, cluster: Option[DeploymentCluster], service: DeploymentService, app: App): Boolean = cluster.exists { c ⇒
    val appPorts = app.container.flatMap(_.docker).map(_.portMappings.flatMap(_.containerPort)).getOrElse(Nil).toSet
    val servicePorts = portMappings(deployment, c, service, "").map(_.containerPort).toSet
    appPorts == servicePorts
  }

  private def checkEnvironmentVariables(deployment: Deployment, cluster: Option[DeploymentCluster], service: DeploymentService, app: App): Boolean = cluster.exists { c ⇒
    app.env == environment(deployment, c, service)
  }

  private def checkHealth(deployment: Deployment, service: DeploymentService, app: App): Boolean = {
    MarathonHealthCheck.equalHealthChecks(deployment.ports, service.healthChecks.getOrElse(List()), app.healthChecks)
  }

  private def get(workflow: Workflow, replyTo: ActorRef): Unit = {
    log.debug(s"marathon reconcile workflow: ${workflow.name}")
    get(appId(workflow)).foreach {
      case Some(app) if workflow.breed.isInstanceOf[DefaultBreed] ⇒
        replyTo ! ContainerWorkflow(
          workflow,
          Option(containers(app)),
          app.taskStats.map(ts ⇒ MarathonCounts.toServiceHealth(ts.totalSummary.stats.counts))
        )
      case Some(app) if workflow.breed.isInstanceOf[BreedReference] ⇒
        val breedReference = workflow.breed.asInstanceOf[BreedReference]
        log.warning(s"marathon reconcile workflow: ${workflow.name} ${app.id} ${breedReference.name}, expecting a breed instead")
        replyTo ! ContainerWorkflow(workflow, None)
      case _ ⇒ replyTo ! ContainerWorkflow(workflow, None)
    }
  }

  private def get(id: String): Future[Option[App]] = getOrPutIfAbsent(s"r$id", () ⇒ {
    log.info(s"marathon sending request: $id")
    httpClient.get[AppsResponse](s"$url/v2/apps?id=$id&embed=apps.tasks&embed=apps.taskStats", headers, logError = false)
      .recover {
        case t: Throwable ⇒
          log.error(t, s"Error while getting app id: $id => ${t.getMessage}")
          None
        case _ ⇒
          log.warning(s"Unknown error while getting app id: $id")
          None
      }
      .map {
        case apps: AppsResponse ⇒
          log.debug(s"apps: for $id => $apps")
          apps.apps.find(app ⇒ app.id == id)
        case _ ⇒
          log.info(s"no app: for $id")
          None
      }
  })(readTimeToLivePeriod)

  private def noGlobalOverride(arg: Argument): MarathonApp ⇒ MarathonApp = identity[MarathonApp]

  private def applyGlobalOverride(workflowDeployment: Boolean): PartialFunction[Argument, MarathonApp ⇒ MarathonApp] = {
    case Argument("override.workflow.docker.network", networkOverrideValue) ⇒
      app ⇒
        if (workflowDeployment)
          app.copy(container = app.container.map(c ⇒ c.copy(docker = c.docker.copy(
            network = networkOverrideValue,
            portMappings = c.docker.portMappings.map(portMapping ⇒ networkOverrideValue match {
              case "USER" ⇒ portMapping.copy(hostPort = None)
              case _      ⇒ portMapping
            })
          ))))
        else app

    case Argument("override.deployment.docker.network", networkOverrideValue) ⇒
      app ⇒
        if (!workflowDeployment)
          app.copy(container = app.container.map(c ⇒ c.copy(docker = c.docker.copy(
            network = networkOverrideValue,
            portMappings = c.docker.portMappings.map(portMapping ⇒ networkOverrideValue match {
              case "USER" ⇒ portMapping.copy(hostPort = None)
              case _      ⇒ portMapping
            })
          ))))
        else app

    case arg @ Argument("override.workflow.docker.privileged", runPrivileged) ⇒
      app ⇒
        if (workflowDeployment)
          Try(runPrivileged.toBoolean).map(
            privileged ⇒ app.copy(container = app.container.map(c ⇒ c.copy(docker = c.docker.copy(privileged = privileged))))
          ).getOrElse(throw NotificationErrorException(InvalidArgumentValueError(arg), s"${arg.key} -> ${arg.value}"))
        else app

    case arg @ Argument("override.deployment.docker.privileged", runPrivileged) ⇒
      app ⇒
        if (!workflowDeployment)
          Try(runPrivileged.toBoolean).map(
            privileged ⇒ app.copy(container = app.container.map(c ⇒ c.copy(docker = c.docker.copy(privileged = privileged))))
          ).getOrElse(throw NotificationErrorException(InvalidArgumentValueError(arg), s"${arg.key} -> ${arg.value}"))
        else app

    case Argument("override.workflow.ipAddress.networkName", networkName) ⇒
      app ⇒
        if (workflowDeployment)
          app.copy(ipAddress = Some(MarathonAppIpAddress(resolveWithNamespace(networkName))))
        else app

    case Argument("override.deployment.ipAddress.networkName", networkName) ⇒
      app ⇒
        if (!workflowDeployment)
          app.copy(ipAddress = Some(MarathonAppIpAddress(resolveWithNamespace(networkName))))
        else app

    case Argument("override.workflow.fetch.uri", uriValue) ⇒
      app ⇒
        if (workflowDeployment)
          app.copy(fetch =
            app.fetch match {
              case None    ⇒ Some(List(UriObject(uriValue)))
              case Some(l) ⇒ Some(UriObject(uriValue) :: l)
            })
        else app

    case Argument("override.deployment.fetch.uri", uriValue) ⇒
      app ⇒
        if (!workflowDeployment)
          app.copy(fetch =
            app.fetch match {
              case None    ⇒ Some(List(UriObject(uriValue)))
              case Some(l) ⇒ Some(UriObject(uriValue) :: l)
            })
        else app

    case arg @ Argument("override.workflow.noHealthChecks", noHealthChecks) ⇒
      app ⇒
        if (workflowDeployment)
          Try(noHealthChecks.toBoolean).map(
            noHealthChecks ⇒ if (noHealthChecks) {
              app.copy(healthChecks = Nil)
            }
            else app
          ).getOrElse(throw NotificationErrorException(InvalidArgumentValueError(arg), s"${arg.key} -> ${arg.value}"))
        else app

    case arg @ Argument("override.deployment.noHealthChecks", noHealthChecks) ⇒
      app ⇒
        if (!workflowDeployment)
          Try(noHealthChecks.toBoolean).map(
            noHealthChecks ⇒ if (noHealthChecks) {
              app.copy(healthChecks = Nil)
            }
            else app
          ).getOrElse(throw NotificationErrorException(InvalidArgumentValueError(arg), s"${arg.key} -> ${arg.value}"))
        else app

    case Argument(argName, argValue) if argName.startsWith("override.workflow.labels.") ⇒
      app ⇒
        if (workflowDeployment) {
          val labelName = argName.drop("override.workflow.labels.".length)
          app.copy(labels = app.labels + (labelName → argValue))
        }
        else app

    case Argument(argName, argValue) if argName.startsWith("override.deployment.labels.") ⇒
      app ⇒
        if (!workflowDeployment) {
          val labelName = argName.drop("override.deployment.labels.".length)
          app.copy(labels = app.labels + (labelName → argValue))
        }
        else app
  }

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {

    validateDeployable(service.breed.deployable)

    val id = appId(deployment, service.breed)
    val name = s"${deployment.name} / ${service.breed.deployable.definition}"
    if (update) log.info(s"marathon update service: $name") else log.info(s"marathon create service: $name")
    val constraints = (namespaceConstraint +: Nil).filter(_.nonEmpty)

    log.info(s"Deploying Deployment and using Arguments:: ${service.arguments}")

    val app = MarathonApp(
      id,
      container(deployment, cluster, service.copy(arguments = service.arguments.filterNot(applyGlobalOverride(false).isDefinedAt))),
      None,
      service.scale.get.instances,
      service.scale.get.cpu.value,
      Math.round(service.scale.get.memory.value).toInt,
      environment(deployment, cluster, service),
      cmd(deployment, cluster, service),
      healthChecks = retrieveHealthChecks(cluster, service).map(MarathonHealthCheck.apply(service.breed.ports, _)),
      labels = labels(deployment, cluster, service),
      constraints = constraints,
      fetch = None
    )

    // Iterate through all Argument objects and if they represent an override, apply them
    val appWithGlobalOverrides = service.arguments.foldLeft(app)((app, argument) ⇒
      applyGlobalOverride(false).applyOrElse(argument, noGlobalOverride)(app))

    val asd = requestPayload(deployment, cluster, service, purge(appWithGlobalOverrides))

    log.info(s"Deploying $asd")
    sendRequest(update, id, asd)
  }

  private def deploy(workflow: Workflow, update: Boolean): Future[Any] = {

    val breed = workflow.breed.asInstanceOf[DefaultBreed]

    validateDeployable(breed.deployable)

    val id = appId(workflow)
    if (update) log.info(s"marathon update workflow: ${workflow.name}") else log.info(s"marathon create workflow: ${workflow.name}")
    val scale = workflow.scale.get.asInstanceOf[DefaultScale]
    val constraints = (namespaceConstraint +: Nil).filter(_.nonEmpty)

    log.info(s"Deploying Workflow and using Arguments:: ${workflow.arguments}")

    val marathonApp = MarathonApp(
      id,
      container(workflow.copy(arguments = workflow.arguments.filterNot(applyGlobalOverride(true).isDefinedAt))),
      None,
      scale.instances,
      scale.cpu.value,
      Math.round(scale.memory.value).toInt,
      environment(workflow),
      cmd(workflow),
      labels = labels(workflow),
      healthChecks = retrieveHealthChecks(workflow).map(MarathonHealthCheck.apply(breed.ports, _)),
      constraints = constraints,
      fetch = None
    )

    // Iterate through all Argument objects and if they represent an override, apply them
    val marathonAppWithGlobalOverrides = workflow.arguments.foldLeft(marathonApp)((app, argument) ⇒
      applyGlobalOverride(true).applyOrElse(argument, noGlobalOverride)(app))

    val toDeploy = requestPayload(workflow, purge(marathonAppWithGlobalOverrides))
    log.info(s"Deploying ${workflow.name} with id $id")
    sendRequest(update, id, toDeploy)
  }

  private def purge(app: MarathonApp): MarathonApp = {
    // workaround - empty args may cause Marathon to reject the request, so removing args altogether
    if (app.args.isEmpty) app.copy(args = null) else app
  }

  private def sendRequest(update: Boolean, id: String, payload: JValue) = {
    if (update) {
      get(id).flatMap { response ⇒
        val changed = Extraction.decompose(response).children.headOption match {
          case Some(app) ⇒ app.diff(payload).changed
          case None      ⇒ payload
        }
        if (changed != JNothing) getOrPutIfAbsent(s"w$id", () ⇒ httpClient.put[Any](s"$url/v2/apps/$id", changed, headers))(writeTimeToLivePeriod)
        else {
          log.info(s"Nothing has changed in app $id configuration")
          Future.successful(false)
        }
      }
    }
    else {
      getOrPutIfAbsent(s"w$id", () ⇒ {
        httpClient.post[Any](s"$url/v2/apps", payload, headers, logError = false).recover {
          case t if t.getMessage != null && t.getMessage.contains("already exists") ⇒ // ignore, sync issue
          case t ⇒
            log.error(t, t.getMessage)
            Future.failed(t)
        }
      })(writeTimeToLivePeriod)
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
    getOrPutIfAbsent(s"w$id", () ⇒ httpClient.delete(s"$url/v2/apps/$id", headers, logError = false) recover { case _ ⇒ None })(writeTimeToLivePeriod)
  }

  private def undeploy(workflow: Workflow) = {
    val id = appId(workflow)
    log.info(s"marathon delete workflow: ${workflow.name}")
    getOrPutIfAbsent(s"w$id", () ⇒ httpClient.delete(s"$url/v2/apps/$id", headers, logError = false) recover { case _ ⇒ None })(writeTimeToLivePeriod)
  }

  private def containers(app: App): Containers = {
    log.info("[Marathon Driver . containers]")
    val scale = DefaultScale(Quantity(app.cpus), MegaByte(app.mem), app.instances)
    val instances = app.tasks.map(task ⇒ {
      val portsAndIpForUserNetwork = for {
        container ← app.container
        docker ← container.docker
        networkName ← Option(docker.network.getOrElse("")) // This is a hack to support 1.4 and 1.5 at the same time
        ipAddressToUse ← task.ipAddresses.headOption
        if (networkName == "USER"
          || app.networks.map(_.mode).contains("container"))
      } yield (ipAddressToUse.ipAddress,
        docker.portMappings.flatMap(_.containerPort) ++ container.portMappings.flatMap(_.containerPort))
      portsAndIpForUserNetwork match {
        case None ⇒
          val network = Try(app.container.get.docker.get.network.get).getOrElse("Empty")
          log.info(s"Ports for ${task.id} => ${task.ports} network: $network")
          ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)
        case Some(portsAndIp) ⇒
          val network = Try(app.container.get.docker.get.network.get).getOrElse("Empty")
          log.info(s"Ports (USER network) for ${task.id} => ${portsAndIp._2} network: $network")
          ContainerInstance(task.id, portsAndIp._1, portsAndIp._2, task.startedAt.isDefined)
      }
    })
    Containers(scale, instances)
  }
}
