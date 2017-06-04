package io.vamp.lifter.artifact

import java.nio.file.{ Path, Paths }

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Config
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ CommonActorLogging, CommonProvider, CommonSupportForActors }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.artifact.{ DefaultBreed, Deployable }
import io.vamp.model.reader.WorkflowReader
import io.vamp.operation.controller.{ ArtifactApiController, DeploymentApiController }
import io.vamp.operation.notification.InternalServerError
import io.vamp.persistence.PersistenceActor

import scala.concurrent.Future
import scala.io.Source

object ArtifactInitializationActor {

  private[artifact] object Load

}

class ArtifactInitializationActor extends ArtifactLoader with CommonSupportForActors with LifterNotificationProvider {

  import ArtifactInitializationActor._

  implicit lazy val timeout = PersistenceActor.timeout()

  private lazy val force = Config.boolean("vamp.lifter.artifact.override")()

  private lazy val postpone = Config.duration("vamp.lifter.artifact.postpone")()

  private lazy val files = Config.stringList("vamp.lifter.artifact.files")()

  private lazy val resources = Config.stringList("vamp.lifter.artifact.resources")()

  def receive = {
    case Load ⇒ (fileLoad andThen resourceLoad)(Unit)
    case _    ⇒
  }

  override def preStart(): Unit = {
    try {
      context.system.scheduler.scheduleOnce(postpone, self, Load)
    } catch {
      case t: Throwable ⇒ reportException(InternalServerError(t))
    }
  }

  private def fileLoad: Unit ⇒ Unit = { _ ⇒ loadFiles(force)(files) }

  private def resourceLoad: Unit ⇒ Unit = { _ ⇒ loadResources(force)(resources) }
}

trait ArtifactLoader extends ArtifactApiController with DeploymentApiController {
  this: CommonActorLogging with CommonProvider ⇒

  implicit def timeout: Timeout

  protected def loadFiles(force: Boolean = false): List[String] ⇒ Unit = {
    _.foreach(file ⇒ load(Paths.get(file), Source.fromFile(file).mkString, force))
  }

  protected def loadResources(force: Boolean = false): List[String] ⇒ Unit = {
    _.map(Paths.get(_)).foreach(path ⇒ load(path, Source.fromInputStream(getClass.getResourceAsStream(path.toString)).mkString, force))
  }

  private def load(path: Path, source: String, force: Boolean): Unit = {

    val `type` = path.getParent.getFileName.toString
    val fileName = path.getFileName.toString
    val name = fileName.substring(0, fileName.lastIndexOf("."))

    exists(`type`, name).map {
      case true ⇒
        if (force) {
          log.info(s"Updating artifact: ${`type`}/$name")
          create(`type`, fileName, name, source)
        } else
          log.info(s"Ignoring creation of artifact because it exists: ${`type`}/$name")

      case false ⇒
        log.info(s"Creating artifact: ${`type`}/$name")
        create(`type`, fileName, name, source)
    }
  }

  private def exists(`type`: String, name: String): Future[Boolean] = {
    readArtifact(`type`, name, expandReferences = false, onlyReferences = false).map {
      case Some(_) ⇒ true
      case _       ⇒ false
    }
  }

  private def create(`type`: String, fileName: String, name: String, source: String) = {
    if (`type` == "breeds" && fileName.endsWith(".js"))
      actorFor[PersistenceActor] ? PersistenceActor.Update(DefaultBreed(name, Map(), Deployable("application/javascript", source), Nil, Nil, Nil, Nil, Map(), None), Some(source))
    else if (`type` == "workflows")
      actorFor[PersistenceActor] ? PersistenceActor.Update(WorkflowReader.read(source), Some(source))
    else
      updateArtifact(`type`, name, source, validateOnly = false)
  }
}
