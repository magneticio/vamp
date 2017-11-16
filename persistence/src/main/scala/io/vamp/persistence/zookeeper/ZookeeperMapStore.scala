package io.vamp.persistence.zookeeper

import java.util
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.hazelcast.core.{HazelcastInstance, MapLoaderLifecycleSupport, MapStore}
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.Artifact
import io.vamp.model.artifact.Template
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.{RetryNTimes, RetryOneTime}

import scala.collection.JavaConverters._

class ZookeeperMapStore extends MapStore[String, Any] with MapLoaderLifecycleSupport with LazyLogging {

  private def getBytes(value: Any) = {
    value.asInstanceOf[Artifact].kind match {
      case "templates" => value.asInstanceOf[Template].definition
    }
  }

  private var cli: CuratorFramework = _

  override def deleteAll(keys: util.Collection[String]): Unit = {}

  override def store(key: String, value: Any): Unit = {
    logger.info("Store "+key)
    val bytes = getBytes(value)
    cli.create().creatingParentsIfNeeded().forPath("/"+key, value.toString.getBytes )
  }

  override def delete(key: String): Unit = {}

  override def storeAll(map: util.Map[String, Any]) = {
    map.asScala.foreach{ case (k,v) => store(k, v) }
  }

  override def init(hazelcastInstance: HazelcastInstance, properties: Properties, mapName: String) = {
    cli = CuratorFrameworkFactory.newClient(properties.getProperty("connectionString"), new RetryOneTime(2000))
    cli.start()
    logger.info("Waiting to connect "+properties.getProperty("connectionString"))
    cli.blockUntilConnected(5, TimeUnit.MINUTES)
    logger.info("Connected")
  }

  override def destroy(): Unit = {
    cli.close()
  }

  override def loadAllKeys(): util.List[String] = {
    List[String]().asJava
  }

  override def loadAll(keys: util.Collection[String]): java.util.Map[String, Any] = {
    Map[String, Any]().empty.asJava
  }

  override def load(key: String): Any = { null }
}
