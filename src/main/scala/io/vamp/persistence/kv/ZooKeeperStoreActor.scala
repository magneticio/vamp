package io.vamp.persistence.kv

import java.io._
import java.net.Socket

import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.persistence.kv.AsyncResponse.{ DataResponse, FailedAsyncResponse }
import org.apache.zookeeper.KeeperException.Code

import scala.concurrent.Future
import scala.language.postfixOps

class ZooKeeperStoreActor extends KeyValueStoreActor with ZooKeeperServerStatistics {

  private val config = ConfigFactory.load().getConfig("vamp.persistence.key-value-store.zookeeper")

  private val servers = config.getString("servers")

  private var zooKeeperClient: Option[AsyncZooKeeperClient] = None

  override protected def info(): Future[Any] = zooKeeperClient match {
    case Some(zk) ⇒ zkVersion(servers) map {
      case version ⇒
        Map(
          "type" -> "zookeeper",
          "zookeeper" -> (Map("version" -> version) ++ (zk.underlying match {
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
        )
    }
    case None ⇒ Future.successful {
      None
    }
  }

  override protected def all(path: List[String]): Future[Map[String, String]] = Future.successful(Map())

  override protected def get(path: List[String]): Future[Option[String]] = zooKeeperClient match {
    case Some(zk) ⇒ zk.get(pathToString(path)) recoverWith {
      case failure: FailedAsyncResponse if failure.code == Code.NONODE ⇒ Future.successful {
        None
      }
      case failure ⇒
        // something is going wrong with the connection
        initClient()
        Future.successful {
          None
        }
    } map {
      case None                   ⇒ None
      case response: DataResponse ⇒ response.data.map(new String(_))
    }

    case None ⇒ Future.successful {
      None
    }
  }

  override protected def set(path: List[String], data: Option[String]): Unit = zooKeeperClient match {
    case Some(zk) ⇒
      zk.get(pathToString(path)) recoverWith {
        case _ ⇒ zk.createPath(pathToString(path))
      } onComplete {
        case _ ⇒ zk.set(pathToString(path), data.map(_.getBytes)) onFailure {
          case failure ⇒ log.error(failure, failure.getMessage)
        }
      }
    case _ ⇒
  }

  private def initClient() = zooKeeperClient = Option {
    AsyncZooKeeperClient(
      servers = servers,
      sessionTimeout = config.getInt("session-timeout"),
      connectTimeout = config.getInt("connect-timeout"),
      basePath = "",
      watcher = None,
      eCtx = actorSystem.dispatcher
    )
  }

  override def preStart() = initClient()

  override def postStop() = zooKeeperClient.foreach {
    _.close()
  }
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
