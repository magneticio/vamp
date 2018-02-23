package io.vamp.persistence.zookeeper

import java.io.{ BufferedReader, InputStreamReader, OutputStream }
import java.net.Socket

import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger
import io.vamp.common.Namespace
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

private case class SharedZooKeeperClient(client: ZooKeeperClient, counter: Int)

object ZooKeeperClient {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val clients = new mutable.HashMap[ZooKeeperConfig, SharedZooKeeperClient]()

  def acquire(config: ZooKeeperConfig)(implicit namespace: Namespace, system: ActorSystem): ZooKeeperClient = synchronized {
    logger.info(s"acquiring ZooKeeper connection: ${config.servers}")
    val client = clients.get(config) match {
      case Some(shared) ⇒
        clients.put(config, shared.copy(counter = shared.counter + 1))
        shared.client
      case None ⇒
        logger.info(s"creating new ZooKeeper connection: ${config.servers}")
        val shared = SharedZooKeeperClient(new ZooKeeperClient(config), 1)
        clients.put(config, shared)
        shared.client
    }
    client
  }

  def reconnect(config: ZooKeeperConfig)(implicit system: ActorSystem): Unit = synchronized {
    clients.get(config) match {
      case Some(shared) ⇒
        shared.client.close()
        clients.put(config, shared.copy(client = new ZooKeeperClient(config)))
      case None ⇒
        logger.info(s"creating new ZooKeeper connection: ${config.servers}")
        val shared = SharedZooKeeperClient(new ZooKeeperClient(config), 1)
        clients.put(config, shared)
    }
  }

  def release(config: ZooKeeperConfig)(implicit namespace: Namespace): Unit = synchronized {
    logger.info(s"releasing ZooKeeper connection: ${config.servers}")
    clients.get(config) match {
      case Some(shared) if shared.counter == 1 ⇒
        logger.info(s"closing ZooKeeper connection: ${config.servers}")
        clients.remove(config)
        shared.client.close()
      case Some(shared) ⇒
        clients.put(config, shared.copy(counter = shared.counter - 1))
      case None ⇒
    }
  }
}

class ZooKeeperClient(val config: ZooKeeperConfig)(implicit system: ActorSystem) {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val pattern = "^(.*?):(\\d+?)(,|\\z)".r

  private implicit val ec: ExecutionContext = system.dispatcher

  val connection: AsyncZooKeeperConnection = {
    logger.info("establishing Zookeeper client connection")
    AsyncZooKeeperConnection(
      servers = config.servers,
      sessionTimeout = config.sessionTimeout,
      connectTimeout = config.connectTimeout,
      basePath = "",
      watcher = None,
      eCtx = system.dispatcher
    )
  }

  def close(): Unit = connection.close()

  def info(): Future[Map[String, _]] = Future {
    val v = version(config.servers)
    Map(
      "type" → "zookeeper",
      "zookeeper" → (Map("version" → v) ++ (connection.underlying match {
        case Some(zookeeper) ⇒
          logger.info(s"Getting Zookeeper info for servers: ${config.servers}")
          val state = zookeeper.getState
          Map(
            "client" → Map(
              "servers" → config.servers,
              "state" → state.toString,
              "session" → zookeeper.getSessionId,
              "timeout" → (if (state.isConnected) zookeeper.getSessionTimeout else "")
            )
          )

        case _ ⇒
          logger.error(s"Zookeeper connection failed to servers: ${config.servers}")
          Map("error" → "no connection")
      }))
    )
  }

  private def version(servers: String): String = {
    servers.split(",").headOption.map {
      case pattern(host, port, _) ⇒
        val sock: Socket = new Socket(host, port.toInt)
        var reader: BufferedReader = null

        try {
          val out: OutputStream = sock.getOutputStream
          out.write("stat".getBytes)
          out.flush()
          sock.shutdownOutput()

          reader = new BufferedReader(new InputStreamReader(sock.getInputStream))
          val marker = "Zookeeper version: "
          var line: String = reader.readLine
          while (line != null && !line.startsWith(marker)) line = reader.readLine
          if (line == null) "" else line.substring(marker.length)

        }
        finally {
          sock.close()
          if (reader != null) reader.close()
        }
      case _ ⇒ ""
    }.getOrElse("")
  }
}
