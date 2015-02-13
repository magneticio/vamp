package io.magnetic.vamp_core.persistence.slick.operations.actor

import akka.actor.Actor
import akka.actor.Actor.Receive
import io.magnetic.vamp_core.persistence.slick.model.Schema
import scala.slick.driver.JdbcDriver.simple._


abstract class DbActor(schema: Schema) extends Actor{

  final protected def process(msg: Any): Unit = {
    try {
      schema.session.withTransaction {
        dbOperations(msg).applyOrElse(msg, unhandled)
      }
    } catch {
      case ex: Throwable => sender ! ex
    }
  }
  
  def dbOperations(msg: Any): Receive
  
  final override def receive: Actor.Receive = {
    case msg: Any => process(msg)
  }
}
  
  
