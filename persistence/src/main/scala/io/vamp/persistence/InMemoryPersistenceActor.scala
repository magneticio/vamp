package io.vamp.persistence

import io.vamp.common.{ Artifact, ClassMapper }

import scala.concurrent.Future

class InMemoryPersistenceActorMapper extends ClassMapper {
  val name = "in-memory"
  val clazz: Class[_] = classOf[InMemoryPersistenceActor]
}

class InMemoryPersistenceActor extends InMemoryRepresentationPersistenceActor {

  override protected def info() = super.info().map(_ + ("type" → "in-memory [no persistence]"))

  protected def set(artifact: Artifact) = Future.successful(setArtifact(artifact))

  protected def delete(name: String, `type`: Class[_ <: Artifact]) = Future.successful(deleteArtifact(name, type2string(`type`)).isDefined)
}
