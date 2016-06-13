package io.vamp.lifter.artifact

import java.nio.file.Paths

import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.reader.ScheduledWorkflowReader
import io.vamp.model.workflow.DefaultWorkflow
import io.vamp.operation.controller.ArtifactApiController
import io.vamp.operation.notification.InternalServerError
import io.vamp.persistence.db.PersistenceActor

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source
import scala.language.postfixOps

object ArtifactInitializationActor {

  private[artifact] object Load

}

class ArtifactInitializationActor extends ArtifactApiController with CommonSupportForActors with LifterNotificationProvider {

  import ArtifactInitializationActor._

  private implicit val timeout = PersistenceActor.timeout

  private val config = ConfigFactory.load().getConfig("vamp.lifter.artifact")

  private val force = config.getBoolean("override")

  private val postpone = config.getInt("postpone") seconds

  private val resources = config.getStringList("resources").asScala

  def receive = {
    case Load ⇒ load()
    case _    ⇒
  }

  override def preStart(): Unit = {
    try {
      context.system.scheduler.scheduleOnce(postpone, self, Load)
    } catch {
      case t: Throwable ⇒ reportException(InternalServerError(t))
    }
  }

  private def load() = resources.map(Paths.get(_)).foreach { path ⇒

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
