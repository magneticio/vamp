package io.vamp.core.persistence.store

import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.core.model.artifact.Artifact

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

trait Store {

  def info: Any

  def all(`type`: Class[_ <: Artifact]): List[Artifact]

  def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope

  def create(artifact: Artifact, ignoreIfExists: Boolean = false): Artifact

  def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact]

  def update(artifact: Artifact, create: Boolean = false): Artifact

  def delete(name: String, `type`: Class[_ <: Artifact]): Artifact
}
