package io.vamp.persistence

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import io.vamp.common.akka.{ActorBootstrap, IoC}
import io.vamp.common.{Config, Namespace}
import io.vamp.persistence.refactor.VampPersistence
import akka.pattern.ask

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object PersistenceBootstrap {

  def databaseType()(implicit namespace: Namespace): String = Config.string("vamp.persistence.database.type")().toLowerCase

  def keyValueStoreType()(implicit namespace: Namespace): String = Config.string("vamp.persistence.key-value-store.type")().toLowerCase
}

class PersistenceBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    for {
      keyValue ← new KeyValueBootstrap().createActors
      _ ← initializePersistence
    } yield keyValue
  }

  private def initializePersistence(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    (for {
      persistenceObject <- Future.fromTry(Try(VampPersistence()))
      _ <- persistenceObject.init()
    } yield ()).recover {
      case e => {
        logger.error(s"Caught exception upon initialization of persistence.\n ${e.toString}")
        ()
      }
    }
  }
}

class PersistenceStorageBootstrap extends ActorBootstrap {

  import PersistenceBootstrap._

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    val db = databaseType()
    info(s"Database: $db")

    val dbActor = alias[PersistenceActor](db, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported database type: ${`type`}")
    })

    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    dbActor.map(_ :: Nil)
  }
}

class KeyValueBootstrap extends ActorBootstrap {

  import PersistenceBootstrap._

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    val kv = keyValueStoreType()
    info(s"Key-Value store: $kv")

    val kvActor = alias[KeyValueStoreActor](kv, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported key-value store type: ${`type`}")
    })

    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    kvActor.map(_ :: Nil)
  }
}
