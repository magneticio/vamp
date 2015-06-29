package io.vamp.core.container_driver.marathon

import com.typesafe.scalalogging.Logger
import io.vamp.common.http.RestClient
import io.vamp.core.container_driver._
import io.vamp.core.container_driver.marathon.api.{Docker, _}
import io.vamp.core.container_driver.notification.UndefinedMarathonApplication
import io.vamp.core.model.artifact._
import org.json4s.{DefaultFormats, Extraction}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object MarathonDriver {

  object Schema extends Enumeration {
    val Docker, Cmd, Command = Value
  }

  MarathonDriver.Schema.values
}

class MarathonDriver(ec: ExecutionContext, url: String) extends AbstractContainerDriver(ec) {

  private val logger = Logger(LoggerFactory.getLogger(classOf[MarathonDriver]))

  def info: Future[ContainerInfo] = RestClient.request[Info](s"GET $url/v2/info").map {
    ContainerInfo("marathon", _)
  }

  def all: Future[List[ContainerService]] = {
    logger.debug(s"marathon get all")
    RestClient.request[Apps](s"GET $url/v2/apps?embed=apps.tasks").map(apps => apps.apps.filter(app => processable(app.id)).map(app => containerService(app)))
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    validateSchemaSupport(service.breed.deployable.schema, MarathonDriver.Schema)

    val id = appId(deployment, service.breed)
    if (update) logger.info(s"marathon update app: $id") else logger.info(s"marathon create app: $id")

    val app = MarathonApp(id, container(deployment, cluster, service), service.scale.get.instances, service.scale.get.cpu, service.scale.get.memory, environment(deployment, cluster, service), cmd(deployment, cluster, service))

    sendApp(if (update) s"PUT $url/v2/apps/${app.id}" else s"POST $url/v2/apps", app, cluster.dialects ++ service.dialects)
  }

  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if MarathonDriver.Schema.Docker.toString.compareToIgnoreCase(schema) == 0 => Some(Container(Docker(definition, portMappings(deployment, cluster, service))))
    case _ => None
  }

  private def cmd(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[String] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if MarathonDriver.Schema.Cmd.toString.compareToIgnoreCase(schema) == 0 || MarathonDriver.Schema.Command.toString.compareToIgnoreCase(schema) == 0 => Some(definition)
    case _ => None
  }

  private def sendApp(url: String, app: MarathonApp, dialects: Map[Dialect.Value, Any]) = {
    val dialect = dialects.getOrElse(Dialect.Marathon, Map())

    (app.container, app.cmd, dialect) match {
      case (None, None, map: Map[_, _]) if map.asInstanceOf[Map[String, _]].get("cmd").nonEmpty =>
      case (None, None, _) => throwException(UndefinedMarathonApplication)
      case _ =>
    }

    implicit val formats = DefaultFormats
    RestClient.request[Any](url, Extraction.decompose(dialect) merge Extraction.decompose(app))
  }

  def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    logger.info(s"marathon delete app: $id")
    RestClient.delete(s"$url/v2/apps/$id")
  }

  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, app.mem, app.instances), app.tasks.map(task => ContainerServer(task.id, task.host, task.ports, task.startedAt.isDefined)))
}

