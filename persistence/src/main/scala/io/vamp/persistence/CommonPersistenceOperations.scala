package io.vamp.persistence

import _root_.io.vamp.common.Artifact
import akka.actor.Actor
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.model.artifact.{ BreedReference, DefaultBlueprint, DefaultBreed }

import scala.concurrent.Future

trait PersistenceArtifact extends Artifact {
  val metadata = Map()
}

trait CommonPersistenceOperations extends PersistenceArchive with ArtifactExpansion with ArtifactShrinkage {
  this: PersistenceApi with CommonSupportForActors ⇒

  import PersistenceActor._

  def receive: Actor.Receive = {

    case All(ofType, page, perPage, expandRef, onlyRef) ⇒ reply {
      Future.successful {
        val artifacts = all(ofType, if (page > 0) page else 1, if (perPage > 0) perPage else ArtifactResponseEnvelope.maxPerPage)
        (expandRef, onlyRef) match {
          case (true, false) ⇒ artifacts.copy(response = artifacts.response.map(expandReferences[Artifact]))
          case (false, true) ⇒ artifacts.copy(response = artifacts.response.map(onlyReferences))
          case _             ⇒ artifacts
        }
      }
    }

    case Read(name, ofType, expandRef, onlyRef) ⇒ reply {
      Future.successful {
        val artifact = get(name, ofType)
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

  private def split(artifact: Artifact, each: Artifact ⇒ Artifact): List[Artifact] = artifact match {
    case blueprint: DefaultBlueprint ⇒
      val breeds = blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(each)
      val bp = blueprint.copy(clusters = blueprint.clusters.map { cluster ⇒
        cluster.copy(services = cluster.services.map { service ⇒
          service.copy(breed = BreedReference(service.breed.name))
        })
      })
      breeds :+ each(bp)
    case _ ⇒ each(artifact) :: Nil
  }
}
