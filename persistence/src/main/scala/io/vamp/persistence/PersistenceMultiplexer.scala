package io.vamp.persistence

import io.vamp.common.Artifact
import io.vamp.model.artifact._

trait PersistenceMultiplexer {

  protected def get[A <: Artifact](artifact: A): Option[A]

  protected def get[T <: Artifact](name: String, `type`: Class[T]): Option[T]

  protected def combine(artifact: Option[Artifact]): Option[Artifact] = {
    if (artifact.isDefined) combineArtifact(artifact.get) else None
  }

  protected def combine(artifacts: ArtifactResponseEnvelope): ArtifactResponseEnvelope = {
    artifacts.copy(response = artifacts.response.flatMap(combineArtifact))
  }

  protected def split(artifact: Artifact, each: Artifact ⇒ Artifact): List[Artifact] = artifact match {
    case blueprint: DefaultBlueprint ⇒ blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(each) :+ each(blueprint)
    case _                           ⇒ each(artifact) :: Nil
  }

  private def combineArtifact(artifact: Artifact): Option[Artifact] = artifact match {
    case blueprint: DefaultBlueprint ⇒ combine(blueprint)
    case _                           ⇒ Option(artifact)
  }

  private def combine(blueprint: DefaultBlueprint): Option[DefaultBlueprint] = Option(
    blueprint.copy(
      clusters = blueprint.clusters.map { cluster ⇒
        val services = cluster.services.map { service ⇒
          val breed = service.breed match {
            case b: DefaultBreed ⇒ get(b).getOrElse(BreedReference(b.name))
            case b               ⇒ b
          }
          service.copy(breed = breed)
        }
        cluster.copy(services = services)
      }
    )
  )
}
