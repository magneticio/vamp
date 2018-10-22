package io.vamp.container_driver.marathon

import akka.actor.{ Actor, ActorRef }
import akka.event.LoggingAdapter
import io.vamp.common.akka.ActorExecutionContextProvider
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.NotificationErrorException
import io.vamp.common.util.HashUtil
import io.vamp.common.vitals.InfoRequest
import io.vamp.common.{ ClassMapper, Config, ConfigMagnet }
import io.vamp.container_driver._
import io.vamp.container_driver.marathon.MarathonDriverActor.{ DeployMarathonApp, UnDeployMarathonApp }
import io.vamp.container_driver.notification.{ UndefinedMarathonApplication, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact._
import io.vamp.model.notification.InvalidArgumentValueError
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.resolver.NamespaceValueResolver
import org.json4s.JsonAST.JObject
import org.json4s._
import org.json4s.native.JsonMethods.parse

import scala.concurrent.Future
import scala.util.Try

class MarathonDriverActorMapper extends ClassMapper {
  val name = "marathon"
  val clazz: Class[_] = classOf[MarathonDriverActor]
}

object MarathonDriverActor {

  val mesosConfig = "vamp.container-driver.mesos"

  val marathonConfig = "vamp.container-driver.marathon"

  val namespaceConstraint: ConfigMagnet[List[String]] = Config.stringList(s"$marathonConfig.namespace-constraint")

  object Schema extends Enumeration {
    val Docker, Cmd, Command = Value
  }

  MarathonDriverActor.Schema.values

  val dialect = "marathon"

  case class DeployMarathonApp(request: AnyRef)

  case class UnDeployMarathonApp(request: AnyRef)

}

case class MesosInfo(frameworks: Any, slaves: Any)

case class MarathonDriverInfo(mesos: MesosInfo, marathon: Any)

class MarathonDriverActor
    extends ContainerDriverActor
    with MarathonNamespace
    with ActorExecutionContextProvider
    with ContainerDriver
    with HealthCheckMerger
    with NamespaceValueResolver {

  import ContainerDriverActor._

  private implicit val formats: Formats = DefaultFormats

  private implicit val loggingAdapter: LoggingAdapter = log

  private implicit lazy val httpClient: HttpClient = new HttpClient

  private lazy val config = MarathonClientConfig()

  private lazy val client: MarathonClient = MarathonClient.acquire(config)

  override protected def supportedDeployableTypes: List[DeployableType] = DockerDeployableType :: CommandDeployableType :: Nil

  override def receive: Actor.Receive = {

    case InfoRequest                    ⇒ reply(info)

    case GetNodes                       ⇒ reply(schedulerNodes)
    case GetRoutingGroups               ⇒ reply(routingGroups)

    case Get(services, equality)        ⇒ get(services, equality)
    case d: Deploy                      ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                    ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways)     ⇒ reply(deployedGateways(gateways))

    case GetWorkflow(workflow, replyTo) ⇒ get(workflow, replyTo)
    case d: DeployWorkflow              ⇒ reply(deploy(d.workflow, d.update))
    case u: UndeployWorkflow            ⇒ reply(undeploy(u.workflow))

    case a: DeployMarathonApp           ⇒ reply(deploy(a.request))
    case a: UnDeployMarathonApp         ⇒ reply(undeploy(a.request))

    case any                            ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  override def postStop(): Unit = MarathonClient.release(config)

  private def info: Future[ContainerInfo] = client.info

  private def schedulerNodes: Future[List[SchedulerNode]] = client.nodes().map {
    _.flatMap {
      case node: Map[_, _] ⇒
        Try {
          try {
            val used = node.asInstanceOf[Map[String, AnyVal]]("used_resources").asInstanceOf[Map[String, AnyVal]]
            val resources = node.asInstanceOf[Map[String, AnyVal]]("resources").asInstanceOf[Map[String, AnyVal]]
            SchedulerNode(
              name = HashUtil.hexSha1(node.asInstanceOf[Map[String, String]]("id")),
              capacity = SchedulerNodeSize(Quantity.of(resources.getOrElse("cpus", 0)), MegaByte.of(s"${resources.getOrElse("mem", "0")}M")),
              used = Option(SchedulerNodeSize(Quantity.of(used.getOrElse("cpus", 0)), MegaByte.of(s"${used.getOrElse("mem", "0")}M")))
            )
          }
          catch {
            case e: Exception ⇒
              e.printStackTrace()
              throw e
          }
        }.toOption
      case _ ⇒ None
    }
  }

  private def routingGroups: Future[List[RoutingGroup]] = {
    client.get().map {
      _.flatMap { app ⇒
        (app.id.split('/').filterNot(_.isEmpty).toList match {
          case group :: id :: Nil ⇒ Option(group → id)
          case id :: Nil          ⇒ Option("" → id)
          case _                  ⇒ None
        }) map {
          case (group, id) ⇒
            val containers = instances(app)
            val appPorts = app.container.flatMap(_.docker).map(_.portMappings.map(_.containerPort)).getOrElse(Nil).flatten
            val containerPorts = app.container.map(_.portMappings.map(_.containerPort)).getOrElse(Nil).flatten
            val ports = appPorts ++ containerPorts
            RoutingGroup(
              name = id,
              kind = "app",
              namespace = group,
              labels = app.labels,
              image = app.container.flatMap(_.docker).map(_.image),
              instances = containers.map { container ⇒
                RoutingInstance(
                  ip = container.host,
                  ports = ports.zip(container.ports).map(port ⇒ RoutingInstancePort(port._1, port._2))
                )
              }
            )
        }
      }
    }
  }

  private def get(deploymentServices: List[DeploymentServices], equalityRequest: ServiceEqualityRequest): Unit = {
    log.info("getting deployment services")
    val replyTo = sender()
    deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).foreach {
      case (deployment, service) ⇒
        val id = appId(deployment, service.breed)
        log.debug(s"marathon sending request: $id")
        client.get(id).foreach {
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
    logger.info("CheckPorts for deployment {} network {}", deployment.name, service.network.getOrElse("Empty"))
    val appPorts = app.container.map(_.portMappings.flatMap(_.containerPort)).getOrElse(Nil).toSet
    val containerPorts = app.container.flatMap(_.docker).map(_.portMappings.flatMap(_.containerPort)).getOrElse(Nil).toSet
    val servicePorts = portMappings(deployment, c, service, "").map(_.containerPort).toSet
    // due to changes in Marathon 1.5.x, both container (docker) and app port mapping should be chacked
    appPorts == servicePorts || containerPorts == servicePorts
  }

  private def checkEnvironmentVariables(deployment: Deployment, cluster: Option[DeploymentCluster], service: DeploymentService, app: App): Boolean = cluster.exists { c ⇒
    app.env == environment(deployment, c, service)
  }

  private def checkHealth(deployment: Deployment, service: DeploymentService, app: App): Boolean = {
    MarathonHealthCheck.equalHealthChecks(deployment.ports, service.healthChecks.getOrElse(List()), app.healthChecks)
  }

  private def get(workflow: Workflow, replyTo: ActorRef): Unit = {
    log.debug(s"marathon get workflow: ${workflow.name}")
    client.get(appId(workflow)).foreach {
      case Some(app) ⇒
        replyTo ! ContainerWorkflow(
          workflow,
          Option(containers(app)),
          app.taskStats.map(ts ⇒ MarathonCounts.toServiceHealth(ts.totalSummary.stats.counts))
        )
      case _ ⇒ replyTo ! ContainerWorkflow(workflow, None)
    }
  }

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

    log.info(s"Deploying Deployment and using Arguments : ${service.arguments}")

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
    logger.info(s"ServiceDialect for deployment {} service dialect: {}", deployment.name, service.dialects.toString())
    val appWithGlobalOverrides = service.arguments.foldLeft(app)((app, argument) ⇒
      applyGlobalOverride(false).applyOrElse(argument, noGlobalOverride)(app))
    val asd = requestPayload(deployment, cluster, service, purge(appWithGlobalOverrides))

    log.info(s"Deploying $asd")
    deploy(update, id, asd)
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
    deploy(update, id, toDeploy)
  }

  private def purge(app: MarathonApp): MarathonApp = {
    // workaround - empty args may cause Marathon to reject the request, so removing args altogether
    if (app.args.isEmpty) app.copy(args = null) else app
  }

  private def deploy(update: Boolean, id: String, payload: JValue) = {
    if (update) {
      log.debug(s"marathon sending request: $id")
      client.get(id).flatMap { response ⇒
        val changed = Extraction.decompose(response).children.headOption match {
          case Some(app) ⇒ app.diff(payload).changed
          case None      ⇒ payload
        }
        if (changed != JNothing) client.put(id, changed)
        else {
          log.info(s"Nothing has changed in app $id configuration")
          Future.successful(false)
        }
      }
    }
    else client.post(id, payload)
  }

  private def container(workflow: Workflow): Option[Container] = {
    if (DockerDeployableType.matches(workflow.breed.asInstanceOf[DefaultBreed].deployable)) Some(Container(docker(workflow))) else None
  }

  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = {
    logger.info("service breed deployable {}", service.breed.deployable.toString)
    if (DockerDeployableType.matches(service.breed.deployable)) Some(Container(docker(deployment, cluster, service))) else {
      logger.info("MarathonDriverActor container deployable check for {}", deployment.name)
      None
    }
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
    client.delete(id)
  }

  private def undeploy(workflow: Workflow) = {
    val id = appId(workflow)
    log.info(s"marathon delete workflow: ${workflow.name}")
    client.delete(id)
  }

  private def containers(app: App): Containers = {
    log.info("[Marathon Driver . containers]")
    val scale = DefaultScale(Quantity(app.cpus), MegaByte(app.mem), app.instances)
    Containers(scale, instances(app))
  }

  private def instances(app: App): List[ContainerInstance] = app.tasks.map { task ⇒
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
        log.debug(s"Ports for ${task.id} => ${task.ports} network: $network")
        ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)
      case Some(portsAndIp) ⇒
        val network = Try(app.container.get.docker.get.network.get).getOrElse("Empty")
        log.debug(s"Ports (USER network) for ${task.id} => ${portsAndIp._2} network: $network")
        ContainerInstance(task.id, portsAndIp._1, portsAndIp._2, task.startedAt.isDefined)
    }
  }

  private def deploy(request: AnyRef): Future[Any] = {
    implicit val formats: DefaultFormats = DefaultFormats
    val r = parse(StringInput(request.toString))
    val app = r.extract[MarathonApp]
    log.info(s"Deploying ${app.id}")
    deploy(update = true, app.id, r)
  }

  private def undeploy(request: AnyRef): Future[Any] = {
    implicit val formats: DefaultFormats = DefaultFormats
    val app = parse(StringInput(request.toString)).extract[MarathonApp]
    log.info(s"marathon delete app: ${app.id}")
    client.delete(app.id)
  }
}
