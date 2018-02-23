package io.vamp.persistence

import _root_.io.vamp.common.{ Artifact, Namespace }
import akka.actor.Actor
import io.vamp.common.akka.{ CommonProvider, CommonSupportForActors }

import scala.concurrent.Future
import scala.reflect._

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

  protected def all[T <: Artifact](`type`: Class[T], page: Int, perPage: Int, filter: (T) ⇒ Boolean = (_: T) ⇒ true): ArtifactResponseEnvelope

  protected def get[T <: Artifact](name: String, `type`: Class[T]): Option[T]

  protected def set[T <: Artifact](artifact: T): T

  protected def delete[T <: Artifact](name: String, `type`: Class[T]): Boolean

  def receive: Actor.Receive = {

    case All(ofType, page, perPage, expandRef, onlyRef) ⇒ reply {
      Future {
        val artifacts = combine(
          all(ofType, if (page > 0) page else 1, if (perPage > 0) perPage else ArtifactResponseEnvelope.maxPerPage)
        )
        (expandRef, onlyRef) match {
          case (true, false) ⇒ artifacts.copy(response = artifacts.response.map(expandReferences[Artifact]))
          case (false, true) ⇒ artifacts.copy(response = artifacts.response.map(onlyReferences))
          case _             ⇒ artifacts
        }
      }
    }

    case Read(name, ofType, expandRef, onlyRef) ⇒ reply {
      Future {
        val artifact = combine(combine(get(name, ofType)))
        (expandRef, onlyRef) match {
          case (true, false) ⇒ expandReferences(artifact)
          case (false, true) ⇒ onlyReferences(artifact)
          case _             ⇒ artifact
        }
      }
    }
    case Create(artifact, source) ⇒ reply {
      Future {
        split(artifact, { artifact: Artifact ⇒
          archiveCreate(
            set(artifact), source
          )
        })
      }
    }

    case Update(artifact, source) ⇒ reply {
      Future {
        split(artifact, { artifact: Artifact ⇒
          archiveUpdate(
            set(artifact), source
          )
        })
      }
    }

    case Delete(name, ofType) ⇒ reply {
      Future {
        remove(name, ofType, { (name, ofType) ⇒
          val result = delete(name, ofType)
          if (result) archiveDelete(name, ofType)
          result
        })
      }
    }
  }

  protected def readExpanded[T <: Artifact: ClassTag](name: String)(implicit namespace: Namespace): Option[T] = {
    get(name, classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]).asInstanceOf[Option[T]]
  }
}
