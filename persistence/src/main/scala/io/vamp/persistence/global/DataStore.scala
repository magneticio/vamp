package io.vamp.persistence.global

import com.hazelcast.config.Config
import com.hazelcast.core.Hazelcast
import com.typesafe.scalalogging.LazyLogging
import info.jerrinot.subzero.SubZero
import io.vamp.common.{Artifact, Id, Namespace}

object DataStore {

  val config = new Config("datastore")
  SubZero.useAsGlobalSerializer(config)
  val hz = Hazelcast.getOrCreateHazelcastInstance(config)

  private val dataStores: scala.collection.mutable.Map[String, DataStore] =
    new scala.collection.concurrent.TrieMap[String, DataStore]()

  def apply(ns: Namespace): DataStore = {
    dataStores.getOrElseUpdate(ns.name, new DataStore(ns))
  }

}

class DataStore(ns: Namespace) extends LazyLogging {

  private def getMapName( className: String ) = {
    logger.info(ns.name + ":" + className)
    ns.name + ":" + className
  }

  def get[T <: Artifact](key: Id[T] ): T = DataStore.hz.getMap(getMapName(key.kind)).get(key.value)

  def put[T <: Artifact](key: Id[T], value: T) = DataStore.hz.getMap(getMapName(key.kind)).put(key.value, value)
}
