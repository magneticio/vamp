package io.vamp.persistence.kv

import java.io._
import java.net.Socket

import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._

import scala.concurrent.Future
import scala.language.postfixOps

class ZooKeeperGatewayStoreActor extends KeyValueStoreActor with ZooKeeperServerStatistics {

  private lazy val config = ConfigFactory.load().getConfig("vamp.persistence.zookeeper")

  private lazy val servers = config.getString("servers")

  private lazy val zk = AsyncZooKeeperClient(
    servers = servers,
    sessionTimeout = config.getInt("session-timeout"),
    connectTimeout = config.getInt("connect-timeout"),
    basePath = config.getString("base-path"),
    watcher = None,
    eCtx = actorSystem.dispatcher
  )

  override protected def info(): Future[Any] = zkVersion(servers) map {
    case version ⇒ "zookeeper" -> (Map("version" -> version) ++ (zk.underlying match {
      case Some(zookeeper) ⇒
        val state = zookeeper.getState
        Map(
          "client" -> Map(
            "servers" -> servers,
            "state" -> state.toString,
            "session" -> zookeeper.getSessionId,
            "timeout" -> (if (state.isConnected) zookeeper.getSessionTimeout else "")
          )
        )
      case _ ⇒ Map("error" -> "no connection")
    }))
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    zk.get(pathToString(path)) map { case response ⇒ response.data.map(new String(_)) }
  }

  override protected def set(path: List[String], data: Option[String]): Unit = {
    zk.get(pathToString(path)) recoverWith {
      case _ ⇒ zk.createPath(pathToString(path))
    } onComplete {
      case _ ⇒ zk.set(pathToString(path), data.map(_.getBytes)) onFailure {
        case failure ⇒ log.error(failure, failure.getMessage)
      }
    }
  }

  override def postStop() = zk.close()

  private def pathToString(path: List[String]) = path.mkString("/")
}

trait ZooKeeperServerStatistics {
  this: ExecutionContextProvider ⇒

  private val pattern = "^(.*?):(\\d+?)(,|\\z)".r

  def zkVersion(servers: String): Future[String] = Future {
    servers match {
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
          while (line != null && !line.startsWith(marker)) {
            line = reader.readLine
          }

          if (line == null) "" else line.substring(marker.length)

        } finally {
          sock.close()
          if (reader != null) {
            reader.close()
          }
        }
      case _ ⇒ ""
    }
  }
}
