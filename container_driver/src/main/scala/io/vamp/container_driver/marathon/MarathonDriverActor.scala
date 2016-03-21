package io.vamp.container_driver.marathon

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver._
import io.vamp.container_driver.marathon.api.{ Docker, _ }
import io.vamp.container_driver.notification.{ UndefinedMarathonApplication, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact._
import io.vamp.model.reader.MegaByte
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.concurrent.Future

object MarathonDriverActor {

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  val mesosUrl = configuration.getString("mesos.url")

  val marathonUrl = configuration.getString("marathon.url")

  object Schema extends Enumeration {
    val Docker, Cmd, Command = Value
  }

  MarathonDriverActor.Schema.values
}

case class MesosInfo(frameworks: Any, slaves: Any)

case class MarathonDriverInfo(mesos: MesosInfo, marathon: Any)

class MarathonDriverActor extends ContainerDriverActor with ContainerDriver {

  import ContainerDriverActor._
  import MarathonDriverActor._

  def receive = {
    case InfoRequest ⇒ reply(info.map(info ⇒ ContainerInfo("marathon", info)))
    case All ⇒ reply(all)
    case Deploy(deployment, cluster, service, update) ⇒ reply(deploy(deployment, cluster, service, update))
    case Undeploy(deployment, service) ⇒ reply(undeploy(deployment, service))
    case any ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = for {
    slaves ← RestClient.get[Any](s"$mesosUrl/master/slaves")
    frameworks ← RestClient.get[Any](s"$mesosUrl/master/frameworks")
    marathon ← RestClient.get[Any](s"$marathonUrl/v2/info")
  } yield {

    val s: Any = slaves match {
      case s: Map[_, _] ⇒ s.asInstanceOf[Map[String, _]].getOrElse("slaves", Nil)
      case _            ⇒ Nil
    }

    ContainerInfo("marathon", MarathonDriverInfo(MesosInfo(frameworks, s), marathon))
  }

  private def all: Future[List[ContainerService]] = {
    log.debug(s"marathon get all")
    RestClient.get[Apps](s"$marathonUrl/v2/apps?embed=apps.tasks").map(apps ⇒ apps.apps.filter(app ⇒ processable(app.id)).map(app ⇒ containerService(app)))
  }

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    validateSchemaSupport(service.breed.deployable.schema, Schema)

    val id = appId(deployment, service.breed)
    if (update) log.info(s"marathon update app: $id") else log.info(s"marathon create app: $id")

    val app = MarathonApp(id, container(deployment, cluster, service), service.scale.get.instances, service.scale.get.cpu, service.scale.get.memory.value, environment(deployment, cluster, service), cmd(deployment, cluster, service))
    val payload = requestPayload(deployment, cluster, service, app)

    if (update)
      RestClient.put[Any](s"$marathonUrl/v2/apps/${app.id}", payload)
    else
      RestClient.post[Any](s"$marathonUrl/v2/apps", payload)
  }

  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if Schema.Docker.toString.compareToIgnoreCase(schema) == 0 ⇒
      val (privileged, arguments) = service.arguments.partition(_.privileged)
      val parameters = arguments.map(argument ⇒ DockerParameter(argument.key, argument.value))
      Some(Container(Docker(definition, portMappings(deployment, cluster, service), parameters, privileged.headOption.exists(_.value.toBoolean))))
    case _ ⇒ None
  }

  private def cmd(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[String] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if Schema.Cmd.toString.compareToIgnoreCase(schema) == 0 || Schema.Command.toString.compareToIgnoreCase(schema) == 0 ⇒ Some(definition)
    case _ ⇒ None
  }

  private def requestPayload(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, app: MarathonApp) = {
    val (local, dialect) = (cluster.dialects.get(Dialect.Marathon), service.dialects.get(Dialect.Marathon)) match {
      case (_, Some(d))    ⇒ Some(service) -> d
      case (Some(d), None) ⇒ None -> d
      case _               ⇒ None -> Map()
    }

    (app.container, app.cmd, dialect) match {
      case (None, None, map: Map[_, _]) if map.asInstanceOf[Map[String, _]].get("cmd").nonEmpty ⇒
      case (None, None, _) ⇒ throwException(UndefinedMarathonApplication)
      case _ ⇒
    }

    implicit val formats: Formats = DefaultFormats
    Extraction.decompose(interpolate(deployment, local, dialect)) merge Extraction.decompose(app)
  }

  private def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    log.info(s"marathon delete app: $id")
    RestClient.delete(s"$marathonUrl/v2/apps/$id")
  }

  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, MegaByte(app.mem), app.instances), app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)))
}
