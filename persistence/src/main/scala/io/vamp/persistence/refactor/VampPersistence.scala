package io.vamp.persistence.refactor

import io.vamp.common.{ Config, Namespace }
import io.vamp.persistence.refactor.api.{ SimpleArtifactPersistenceDao, SimpleArtifactPersistenceDaoFactory }

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
 * Created by mihai on 11/10/17.
 */
object VampPersistence {

  private val persistenceDaoMap: scala.collection.mutable.Map[String, SimpleArtifactPersistenceDao] =
    new scala.collection.concurrent.TrieMap[String, SimpleArtifactPersistenceDao]()

  // TODO: Make only creation of DAO synchronized. Retrieval can be left non-Sync
  def apply()(implicit ns: Namespace): SimpleArtifactPersistenceDao = this.synchronized {
    persistenceDaoMap.getOrElseUpdate(ns.name, createPersistenceDao)
  }

  private def createPersistenceDao(implicit ns: Namespace): SimpleArtifactPersistenceDao = {
    val databaseType: String = Config.string("vamp.persistence.database.type")()
    val databaseClassName: String = Config.string("vamp.persistence.database.class-name")()
    Try(Class.forName(databaseClassName).newInstance.asInstanceOf[SimpleArtifactPersistenceDaoFactory].get(ns))
      .recoverWith {
        case e: ClassNotFoundException ⇒ throw new RuntimeException(s"Unsupported Database type: ${databaseType}")
        case t: Throwable              ⇒ throw t
      }.get
  }
}
