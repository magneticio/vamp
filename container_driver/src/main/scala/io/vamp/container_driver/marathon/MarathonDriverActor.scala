package io.vamp.container_driver.marathon

import com.typesafe.config.ConfigFactory
import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor.ContainerDriveMessage
import io.vamp.container_driver._
import io.vamp.container_driver.notification.{ UndefinedMarathonApplication, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import org.json4s._

import scala.concurrent.Future

object MarathonDriverActor {

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  val mesosUrl = configuration.getString("mesos.url")

  val marathonUrl = configuration.getString("marathon.url")

  object Schema extends Enumeration {
    val Docker, Cmd, Command = Value
  }

  MarathonDriverActor.Schema.values

  case class AllApps(filter: (MarathonApp) ⇒ Boolean) extends ContainerDriveMessage

  case class DeployApp(app: MarathonApp, update: Boolean) extends ContainerDriveMessage

  case class RetrieveApp(app: String) extends ContainerDriveMessage

  case class UndeployApp(app: String) extends ContainerDriveMessage

}

case class MesosInfo(frameworks: Any, slaves: Any)

case class MarathonDriverInfo(mesos: MesosInfo, marathon: Any)

class MarathonDriverActor extends ContainerDriverActor with ContainerDriver {

  import ContainerDriverActor._
  import MarathonDriverActor._

  private implicit val formats: Formats = DefaultFormats

  protected val nameDelimiter = "/"

  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def appId(deployment: Deployment, breed: Breed): String = s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  def receive = {
    case InfoRequest                ⇒ reply(info)
    case All                        ⇒ reply(all)
    case d: Deploy                  ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))
    case a: AllApps                 ⇒ reply(allApps.map(_.filter(a.filter)))
    case d: DeployApp               ⇒ reply(deploy(d.app, d.update))
    case u: UndeployApp             ⇒ reply(undeploy(u.app))
    case r: RetrieveApp             ⇒ reply(retrieve(r.app))
    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
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
    RestClient.get[AppsResponse](s"$marathonUrl/v2/apps?embed=apps.tasks").map(apps ⇒ apps.apps.filter(app ⇒ processable(app.id)).map(app ⇒ containerService(app)))
  }

  private def processable(id: String): Boolean = id.split(nameDelimiter).length == 3

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    validateSchemaSupport(service.breed.deployable.schema, Schema)

    val id = appId(deployment, service.breed)
    if (update) log.info(s"marathon update app: $id") else log.info(s"marathon create app: $id")

    val app = MarathonApp(id, container(deployment, cluster, service), service.scale.get.instances, service.scale.get.cpu.value, Math.round(service.scale.get.memory.value).toInt, environment(deployment, cluster, service), cmd(deployment, cluster, service))

    sendRequest(update, app.id, requestPayload(deployment, cluster, service, purge(app)))
  }

  private def deploy(app: MarathonApp, update: Boolean): Future[Any] = {
    sendRequest(update, app.id, Extraction.decompose(purge(app)))
  }

  private def purge(app: MarathonApp): MarathonApp = {
    // workaround - empty args may cause Marathon to reject the request, so removing args altogether
    if (app.args.isEmpty) app.copy(args = null) else app
  }

  private def sendRequest(update: Boolean, id: String, payload: JValue) = update match {
    case true ⇒ RestClient.get[Any](s"$marathonUrl/v2/apps/$id").flatMap { response ⇒
      val changed = Extraction.decompose(response).children.headOption match {
        case Some(app) ⇒ app.diff(payload).changed
        case None      ⇒ payload
      }
      RestClient.put[Any](s"$marathonUrl/v2/apps/$id", changed)
    }

    case false ⇒ RestClient.post[Any](s"$marathonUrl/v2/apps", payload)
  }

  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if Schema.Docker.toString.compareToIgnoreCase(schema) == 0 ⇒ Some(Container(docker(deployment, cluster, service, definition)))
    case _ ⇒ None
  }

  private def cmd(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[String] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if Schema.Cmd.toString.compareToIgnoreCase(schema) == 0 || Schema.Command.toString.compareToIgnoreCase(schema) == 0 ⇒ Some(definition)
    case _ ⇒ None
  }

  private def requestPayload(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, app: MarathonApp): JValue = {
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

    Extraction.decompose(interpolate(deployment, local, dialect)) merge Extraction.decompose(app)
  }

  private def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    log.info(s"marathon delete app: $id")
    RestClient.delete(s"$marathonUrl/v2/apps/$id")
  }

  private def undeploy(app: String) = {
    log.info(s"marathon delete app: $app")
    RestClient.delete(s"$marathonUrl/v2/apps/$app")
  }

  private def containerService(app: App): ContainerService = {
    ContainerService(nameMatcher(app.id), DefaultScale("", Quantity(app.cpus), MegaByte(app.mem), app.instances), app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)))
  }

  private def allApps: Future[List[MarathonApp]] = {
    Future.successful(Nil)
  }

  private def retrieve(app: String): Future[Option[App]] = {
    RestClient.get[AppResponse](s"$marathonUrl/v2/apps/$app", RestClient.jsonHeaders, logError = false) recover { case _ ⇒ None } map {
      case AppResponse(response) ⇒ Option(response)
      case _                     ⇒ None
    }
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
