package io.vamp.persistence

import io.vamp.common.ClassMapper

class InMemoryPersistenceActorMapper extends ClassMapper {
  val name = "in-memory"
  val clazz: Class[_] = classOf[InMemoryPersistenceActor]
}

class InMemoryPersistenceActor extends PersistenceActor with PersistenceRepresentation {
  override protected def info(): Map[String, Any] = super.info() + ("type" → "in-memory [no persistence]")
}
