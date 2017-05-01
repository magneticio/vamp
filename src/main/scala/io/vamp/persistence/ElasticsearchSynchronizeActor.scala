package io.vamp.persistence

import io.vamp.common.akka.IoC.actorFor
import io.vamp.common._
import io.vamp.common.notification.Notification
import io.vamp.pulse.ElasticsearchClient
import _root_.akka.pattern.ask
import _root_.akka.actor.ActorRef

class ElasticsearchSynchronizeActorMapper extends ClassMapper {

  override def name: String = "elasticsearch"

  override def clazz: Class[_] = classOf[ElasticsearchSynchronizeActor]

}

object ElasticsearchSynchronizeActor {

  val searchUrl: ConfigMagnet[String] = Config.string("vamp.persistence.search.es.url")

  val searchIndex: ConfigMagnet[String] = Config.string("vamp.persistence.search.es.index.name")

}

/**
  * Synchronizes the artifacts store with ElasticSearch for search indexing
  */
class ElasticsearchSynchronizeActor(persistenceActor: ActorRef) extends SearchSynchronizeActor {

  override protected lazy val searchUrl: String = ElasticsearchSynchronizeActor.searchUrl()

  override protected lazy val searchIndex: String = resolveWithNamespace(ElasticsearchSynchronizeActor.searchIndex())

  private lazy val es = new ElasticsearchClient(searchUrl)

  override protected def synchronizeSearch(namespace: String, searchIndex: String): Unit = {
    (persistenceActor ? CQRSActor.RetrieveAll).mapTo[Map[String, Map[String, Artifact]]].foreach { m =>
      println(m)
    }
  }

  // TODO add search synchronize exception
  override def message(notification: Notification): String = ""

  override def info(notification: Notification) = for {
    health ← es.health
    initializationTime ← es.creationTime(searchIndex)
  } yield ElasticsearchPersistenceInfo("elasticsearch", searchUrl, searchIndex, initializationTime, health)

  // TODO add search synchronize exception
  override def reportException(notification: Notification): Exception = new Exception("TODO")

}
