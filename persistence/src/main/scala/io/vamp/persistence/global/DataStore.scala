package io.vamp.persistence.global

import com.hazelcast.config.{Config, MapConfig, MapStoreConfig}
import com.hazelcast.core.{Hazelcast, MapLoader, MapStoreFactory}
import com.typesafe.scalalogging.LazyLogging
import info.jerrinot.subzero.SubZero
import io.vamp.common.{Artifact, Config, Id, Namespace}

object DataStore {

  private val dataStores: scala.collection.mutable.Map[String, DataStore] =
    new scala.collection.concurrent.TrieMap[String, DataStore]()

  def apply(ns: Namespace): DataStore = {
    dataStores.getOrElseUpdate(ns.name, new DataStore(ns))
  }


}

class DataStore(ns: Namespace) extends LazyLogging {

  implicit val namespace: Namespace = ns

  private lazy val hz = {
    val connectionString = io.vamp.common.Config.string("vamp.persistence.key-value-store.zookeeper.connectionString")()

    val config = new Config(ns.name)
    SubZero.useAsGlobalSerializer(config)
    val mapStoreConfig = new MapStoreConfig
    mapStoreConfig.getProperties.setProperty("connectionString", connectionString)
    mapStoreConfig.setEnabled(true)
    mapStoreConfig.setWriteDelaySeconds(0)
    mapStoreConfig.setClassName("io.vamp.persistence.zookeeper.ZookeeperMapStore")
    val mapConfig: MapConfig = config.getMapConfig("*")
    mapConfig.setMapStoreConfig(mapStoreConfig)
    config.addMapConfig(mapConfig)

    Hazelcast.getOrCreateHazelcastInstance(config)
  }

  def get[T <: Artifact](key: Id[T] ): T = hz.getMap(key.kind).get(key.value)

  def put[T <: Artifact](key: Id[T], value: T) = hz.getMap(key.kind).put(key.value, value)
}
