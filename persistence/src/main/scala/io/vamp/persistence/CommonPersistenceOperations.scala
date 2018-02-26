package io.vamp.persistence

import _root_.io.vamp.common.Artifact
import akka.actor.Actor
import io.vamp.common.akka.CommonSupportForActors

import scala.concurrent.Future

trait PersistenceArtifact extends Artifact {
  val metadata = Map()
}

trait CommonPersistenceOperations extends PersistenceArchive with PersistenceMultiplexer with ArtifactExpansion with ArtifactShrinkage {
  this: PersistenceApi with CommonSupportForActors ⇒

  import PersistenceActor._

  def receive: Actor.Receive = {

    case All(ofType, page, perPage, expandRef, onlyRef) ⇒ reply {
      Future.successful {
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
      Future.successful {
        val artifact = combine(combine(get(name, ofType)))
        (expandRef, onlyRef) match {
          case (true, false) ⇒ expandReferences(artifact)
          case (false, true) ⇒ onlyReferences(artifact)
          case _             ⇒ artifact
        }
      }
    }
    case Create(artifact, source) ⇒ reply {
      Future.successful {
        split(artifact, { artifact: Artifact ⇒
          archiveCreate(
            set(artifact), source
          )
        })
      }
    }

    case Update(artifact, source) ⇒ reply {
      Future.successful {
        split(artifact, { artifact: Artifact ⇒
          archiveUpdate(
            set(artifact), source
          )
        })
      }
    }

    case Delete(name, ofType) ⇒ reply {
      Future.successful {
        val result = delete(name, ofType)
        if (result) archiveDelete(name, ofType)
        result
      }
    }
  }
}
