package io.vamp.persistence

import io.vamp.common.ClassMapper

import scala.concurrent.Future

class MySqlPersistenceActorMapper extends ClassMapper {
  val name = "mysql"
  val clazz = classOf[MySqlPersistenceActor]
}

class MySqlPersistenceActor extends SqlPersistenceActor {

  protected def info() = Future.successful(representationInfo() + ("type" → "mysql") + ("url" → url))
}
