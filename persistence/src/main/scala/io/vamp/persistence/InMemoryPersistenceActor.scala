package io.vamp.persistence

import io.vamp.common.{ Artifact, ClassMapper }

class InMemoryPersistenceActorMapper extends ClassMapper {
  val name = "in-memory"
  val clazz: Class[_] = classOf[InMemoryPersistenceActor]
}

class InMemoryPersistenceActor extends InMemoryRepresentationPersistenceActor {

  override protected def info(): Map[String, Any] = super.info() + ("type" → "in-memory [no persistence]")

  protected def set[T <: Artifact](artifact: T): T = setArtifact[T](artifact)

  protected def delete[T <: Artifact](name: String, `type`: Class[T]): Boolean = deleteArtifact(name, type2string(`type`)).isDefined
}
