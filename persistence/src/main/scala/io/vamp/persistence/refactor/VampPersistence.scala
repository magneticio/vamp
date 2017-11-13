package io.vamp.persistence.refactor

import io.vamp.common.{ Config, Namespace }
import io.vamp.persistence.refactor.api.SimpleArtifactPersistenceDao
import io.vamp.persistence.refactor.dao.EsDao

import scala.concurrent.ExecutionContext

/**
 * Created by mihai on 11/10/17.
 */
object VampPersistence {

  private val persistenceDaoMap: scala.collection.mutable.Map[String, SimpleArtifactPersistenceDao] =
    new scala.collection.concurrent.TrieMap[String, SimpleArtifactPersistenceDao]()

  def apply()(implicit ns: Namespace, ec: ExecutionContext): SimpleArtifactPersistenceDao = persistenceDao

  def persistenceDao(implicit ns: Namespace, ec: ExecutionContext): SimpleArtifactPersistenceDao = {
    persistenceDaoMap.getOrElseUpdate(ns.name, createPersistenceDao)
  }

  private def createPersistenceDao(implicit ns: Namespace, ec: ExecutionContext): SimpleArtifactPersistenceDao = {
    val databaseType: String = Config.string("vamp.persistence.database.type")()
    if (databaseType == "elasticsearch") {
      new EsDao(
        namespace = ns,
        elasticSearchHostAndPort = Config.string("vamp.persistence.database.elasticsearch.elasticsearch-url")(),
        elasticSearchClusterName = Config.string("vamp.persistence.database.elasticsearch.elasticsearch-cluster-name")(),
        testingContext = Config.boolean("vamp.persistence.database.elasticsearch.elasticsearch-test-cluster")()
      )
    }
    else throw new RuntimeException(s"Unsupported Database type: ${databaseType}")

  }
}
