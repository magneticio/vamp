package io.vamp.persistence

import akka.actor.Actor
import io.vamp.common.akka.{ CommonProvider, CommonSupportForActors }

import scala.concurrent.Future
import scala.reflect._
import _root_.io.vamp.common.Artifact

trait PersistenceArtifact extends Artifact {
  val metadata = Map()
}

trait CommonPersistenceMessages {

  case class All(`type`: Class[_ <: Artifact], page: Int, perPage: Int, expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceActor.PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None) extends PersistenceActor.PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact], expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceActor.PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None) extends PersistenceActor.PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceActor.PersistenceMessages

}

trait CommonPersistenceOperations extends PersistenceMultiplexer with PersistenceArchive with ArtifactExpansion with ArtifactShrinkage {
  this: CommonSupportForActors with CommonProvider ⇒

  import PersistenceActor._

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope]

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]

  protected def set(artifact: Artifact): Future[Artifact]

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean]

  def receive: Actor.Receive = {

    case All(ofType, page, perPage, expandRef, onlyRef) ⇒ reply {
      all(ofType, if (page > 0) page else 1, if (perPage > 0) perPage else ArtifactResponseEnvelope.maxPerPage)
        .flatMap(combine).flatMap { artifacts ⇒
          (expandRef, onlyRef) match {
            case (true, false) ⇒ Future.sequence(artifacts.response.map(expandReferences)).map { response ⇒ artifacts.copy(response = response) }
            case (false, true) ⇒ Future.successful(artifacts.copy(response = artifacts.response.map(onlyReferences)))
            case _             ⇒ Future.successful(artifacts)
          }
        }
    }

    case Read(name, ofType, expandRef, onlyRef) ⇒ reply {
      get(name, ofType)
        .flatMap(combine).flatMap { artifact ⇒
          (expandRef, onlyRef) match {
            case (true, false) ⇒ expandReferences(artifact)
            case (false, true) ⇒ Future.successful(onlyReferences(artifact))
            case _             ⇒ Future.successful(artifact)
          }
        }
    }

    case Create(artifact, source) ⇒ reply {
      split(artifact, { artifact: Artifact ⇒
        set(artifact) map {
          archiveCreate(_, source)
        }
      })
    }

    case Update(artifact, source) ⇒ reply {
      split(artifact, { artifact: Artifact ⇒
        set(artifact) map {
          archiveUpdate(_, source)
        }
      })
    }

    case Delete(name, ofType) ⇒ reply {
      remove(name, ofType, { (name, ofType) ⇒
        delete(name, ofType) map {
          result ⇒
            if (result) archiveDelete(name, ofType)
            result
        }
      })
    }
  }

  protected def readExpanded[T <: Artifact: ClassTag](name: String): Future[Option[T]] = {
    get(name, classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]).asInstanceOf[Future[Option[T]]]
  }
}
