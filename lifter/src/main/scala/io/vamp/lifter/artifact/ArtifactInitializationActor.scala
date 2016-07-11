package io.vamp.lifter.artifact

import java.nio.file.Paths

import akka.pattern.ask
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.common.config.Config
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.reader.ScheduledWorkflowReader
import io.vamp.model.workflow.DefaultWorkflow
import io.vamp.operation.controller.ArtifactApiController
import io.vamp.operation.notification.InternalServerError
import io.vamp.persistence.db.PersistenceActor

import scala.concurrent.Future
import scala.io.Source

object ArtifactInitializationActor {

  private[artifact] object Load

}

class ArtifactInitializationActor extends ArtifactApiController with CommonSupportForActors with LifterNotificationProvider {

  import ArtifactInitializationActor._

  private implicit val timeout = PersistenceActor.timeout

  private val config = Config.config("vamp.lifter.artifact")

  private val force = config.boolean("override")

  private val postpone = config.duration("postpone")

  private val files = config.stringList("files")

  private val resources = config.stringList("resources")

  def receive = {
    case Load ⇒ (loadFiles andThen loadResources)(Unit)
    case _    ⇒
  }

  override def preStart(): Unit = {
    try {
      context.system.scheduler.scheduleOnce(postpone, self, Load)
    } catch {
      case t: Throwable ⇒ reportException(InternalServerError(t))
    }
  }

  private def loadFiles: Unit ⇒ Unit = { _ ⇒
    files.foreach { file ⇒
      log.info(s"Creating/updating artifacts using file: $file")
      updateArtifacts(Source.fromFile(file).mkString, validateOnly = false)
    }
  }

  private def loadResources: Unit ⇒ Unit = { _ ⇒

    resources.map(Paths.get(_)).foreach { path ⇒

      val `type` = path.getParent.toString
      val fileName = path.getFileName.toString
      val name = fileName.substring(0, fileName.lastIndexOf("."))
      val source = Source.fromInputStream(getClass.getResourceAsStream(path.toString)).mkString

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
  }

  private def exists(`type`: String, name: String): Future[Boolean] = {
    readArtifact(`type`, name, expandReferences = false, onlyReferences = false).map {
      case Some(_) ⇒ true
      case _       ⇒ false
    }
  }

  private def create(`type`: String, fileName: String, name: String, source: String) = {
    if (`type` == "workflows" && fileName.endsWith(".js"))
      actorFor[PersistenceActor] ? PersistenceActor.Update(DefaultWorkflow(name, None, Option(source), None), Some(source))
    else if (`type` == "scheduled-workflows")
      actorFor[PersistenceActor] ? PersistenceActor.Update(ScheduledWorkflowReader.read(source), Some(source))
    else
      updateArtifact(`type`, name, source, validateOnly = false)
  }
}
