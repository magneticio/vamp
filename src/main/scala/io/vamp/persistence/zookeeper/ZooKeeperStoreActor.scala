package io.vamp.persistence.zookeeper

import java.io._
import java.net.Socket

import io.vamp.common.{ ClassMapper, Config }
import io.vamp.common.akka._
import io.vamp.persistence.KeyValueStoreActor
import io.vamp.persistence.zookeeper.AsyncResponse.{ ChildrenResponse, DataResponse, FailedAsyncResponse }
import org.apache.zookeeper.KeeperException.Code

import scala.concurrent.Future

class ZooKeeperStoreActorMapper extends ClassMapper {
  val name = "zookeeper"
  val clazz = classOf[ZooKeeperStoreActor]
}

class ZooKeeperStoreActor extends KeyValueStoreActor with ZooKeeperServerStatistics {

  private val config = "vamp.persistence.key-value-store.zookeeper"
  private val servers = Config.string(s"$config.servers")()
  private var zooKeeperClient: Option[AsyncZooKeeperClient] = None

  override protected def info(): Future[Any] = zooKeeperClient match {
    case Some(zk) ⇒ zkVersion(servers) map { version ⇒
      Map(
        "type" → "zookeeper",
        "zookeeper" → (Map("version" → version) ++ (zk.underlying match {
          case Some(zookeeper) ⇒
            val state = zookeeper.getState
            Map(
              "client" → Map(
                "servers" → servers,
                "state" → state.toString,
                "session" → zookeeper.getSessionId,
                "timeout" → (if (state.isConnected) zookeeper.getSessionTimeout else "")
              )
            )

          case _ ⇒ Map("error" → "no connection")
        }))
      )
    }
    case None ⇒ Future.successful(None)
  }

  override protected def all(path: List[String]): Future[List[String]] = zooKeeperClient match {
    case Some(zk) ⇒

      def collect(path: List[String], addRoot: Boolean = false): Future[List[ChildrenResponse]] = {
        zk.getChildren(pathToString(path)) recoverWith recoverRetrieval(Nil) flatMap {
          case node: ChildrenResponse ⇒
            val children = Future.sequence {
              node.children.map { child ⇒ collect(path :+ child, addRoot = true) }
            }.map(_.flatten.toList)

            if (addRoot) children.map { children ⇒ node +: children } else children

          case _ ⇒ Future.successful(Nil)
        }
      }

      val rootLength = pathToString(path).length + 1

      collect(path).map {
        _.collect {
          case node if node.stat.getDataLength > 0 ⇒ node.path.substring(rootLength)
        }
      }

    case None ⇒ Future.successful(Nil)
  }

  override protected def get(path: List[String]): Future[Option[String]] = zooKeeperClient match {
    case Some(zk) ⇒ zk.get(pathToString(path)) recoverWith recoverRetrieval(None) map {
      case response: DataResponse ⇒ response.data.map(new String(_))
      case _                      ⇒ None
    }
    case None ⇒ Future.successful(None)
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = zooKeeperClient match {
    case Some(zk) ⇒
      zk.get(pathToString(path)) recoverWith {
        case _ ⇒ zk.createPath(pathToString(path))
      } flatMap { _ ⇒
        zk.set(pathToString(path), data.map(_.getBytes)) recoverWith {
          case failure ⇒
            log.error(failure, failure.getMessage)
            Future.failed(failure)
        }
      }
    case _ ⇒ Future.successful(None)
  }

  private def recoverRetrieval[T](default: T): PartialFunction[Throwable, Future[T]] = {
    case failure: FailedAsyncResponse if failure.code == Code.NONODE ⇒ Future.successful(default)
    case failure ⇒
      // something is going wrong with the connection
      initClient()
      Future.successful(default)
  }

  private def initClient() = zooKeeperClient = Option {
    AsyncZooKeeperClient(
      servers = servers,
      sessionTimeout = Config.int(s"$config.session-timeout")(),
      connectTimeout = Config.int(s"$config.connect-timeout")(),
      basePath = "",
      watcher = None,
      eCtx = actorSystem.dispatcher
    )
  }

  override def preStart() = initClient()

  override def postStop() = zooKeeperClient.foreach(_.close())
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
          while (line != null && !line.startsWith(marker)) line = reader.readLine
          if (line == null) "" else line.substring(marker.length)

        } finally {
          sock.close()
          if (reader != null) reader.close()
        }
      case _ ⇒ ""
    }
  }
}
