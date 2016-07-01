package io.vamp.operation.persistence

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.model.artifact.{ Artifact, Deployment, Gateway }
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.operation.persistence.KeyValueSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.persistence.kv.KeyValueStoreActor
import org.json4s.native.Serialization._

import scala.reflect.ClassTag
import scala.util.Success

class KeyValueSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[KeyValueSynchronizationActor] ! SynchronizeAll
}

object KeyValueSynchronizationActor {

  sealed trait KeyValueMessages

  object SynchronizeAll extends KeyValueMessages

  case class Synchronize(path: List[String], artifacts: List[Artifact], stored: String) extends KeyValueMessages

}

class KeyValueSynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import KeyValueSynchronizationActor._

  private implicit val timeout = PersistenceActor.timeout

  def receive = {
    case SynchronizeAll                       ⇒ synchronize()
    case Synchronize(path, artifacts, stored) ⇒ synchronize(path, artifacts, stored)
    case _                                    ⇒
  }

  private def synchronize() = {

    val sendTo = self

    def collect[T <: Artifact: ClassTag](path: List[String]) = {
      (for {
        artifacts ← allArtifacts[T]
        source ← checked[Option[String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(path))
      } yield (artifacts, source)) onComplete {
        case Success((artifacts, source)) ⇒ sendTo ! Synchronize(path, artifacts, source.getOrElse(""))
        case _                            ⇒
      }
    }

    collect[Gateway]("gateways" :: Nil)
    collect[Deployment]("deployments" :: Nil)
  }

  private def synchronize(path: List[String], artifacts: List[Artifact], stored: String) = {
    val json = write(artifacts.sortBy(_.name))(CoreSerializationFormat.full)
    if (json != stored) IoC.actorFor[KeyValueStoreActor] ! KeyValueStoreActor.Set(path, Option(json))
  }
}
