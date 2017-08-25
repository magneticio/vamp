package io.vamp.persistence.zookeeper

import java.io._
import java.net.Socket

import io.vamp.common.{ClassMapper, Config}
import io.vamp.persistence.{KeyValueStoreActor, KeyValueStorePath}
import io.vamp.persistence.zookeeper.AsyncResponse.{ChildrenResponse, DataResponse, FailedAsyncResponse}
import org.apache.zookeeper.KeeperException.Code

import scala.concurrent.Future

class ZooKeeperStoreActorMapper extends ClassMapper {
  val name = "zookeeper"
  val clazz = classOf[ZooKeeperStoreActor]
}

class ZooKeeperStoreActor extends KeyValueStoreActor {

  private val config = "vamp.persistence.key-value-store.zookeeper"

  private lazy val servers = Config.string(s"$config.servers")()

  private var zooKeeperClient: Option[AsyncZooKeeperClient] = None

  override protected def info: Future[Any] = {
    def zkVersion(servers: String): Future[String] = Future {
      val pattern = "^(.*?):(\\d+?)(,|\\z)".r

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

    zooKeeperClient match {
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
  }

  override protected def all(path: KeyValueStorePath): Future[List[String]] = {
    def collectRecursively(path: KeyValueStorePath, zk: AsyncZooKeeperClient, addRoot: Boolean = false): Future[List[ChildrenResponse]] =
      for {
        zkResponse <- zk.getChildren(path.toPathString) recoverWith recoverRetrievalAndReturnDefault(Nil)
        childrenResponse <- zkResponse match {
          case node: ChildrenResponse ⇒
            val children = Future.sequence {
              node.children.map { child ⇒ collectRecursively(path :+ child, zk, addRoot = true) }
            }.map(_.flatten.toList)

            if (addRoot) children.map { children ⇒ node +: children } else children

          case _ ⇒ Future.successful(Nil)
        }
      } yield childrenResponse

    zooKeeperClient match {
      case Some(zk) ⇒
        val rootLength = path.pathStringLength(this) + 1
        collectRecursively(path, zk).map {
          _.filter(_.stat.getDataLength > 0).map(_.path.substring(rootLength))
        }
      case None ⇒ Future.successful(Nil)
    }
  }

  override protected def get(path: KeyValueStorePath): Future[Option[String]] = zooKeeperClient match {
    case Some(zk) ⇒ zk.get(path.toPathString) recoverWith recoverRetrievalAndReturnDefault(default = None) map {
      case response: DataResponse ⇒ response.data.map(new String(_))
      case _                      ⇒ None
    }
    case None ⇒ Future.successful(None)
  }

  override protected def set(path: KeyValueStorePath, data: Option[String]): Future[Option[AsyncResponse.StatResponse]] = zooKeeperClient match {
    case Some(zk) ⇒
      for {
        _ <- zk.get(path.toPathString) recoverWith {
          case _ ⇒ zk.createPath(path.toPathString)
        }
        response <- zk.set(path.toPathString, data.map(_.getBytes)) recoverWith {
          case failure ⇒
            log.error(failure, failure.getMessage)
            Future.failed(failure)
        }
      } yield Some(response)
    case _ ⇒ Future.successful(None)
  }

  private def recoverRetrievalAndReturnDefault[T](default: T): PartialFunction[Throwable, Future[T]] = {
    case failure: FailedAsyncResponse if failure.code == Code.NONODE ⇒ Future.successful(default)
    case _ ⇒
      // something is going wrong with the connection
      initClient()
      Future.successful(default)
  }

  private def initClient() = {
    zooKeeperClient.map(_.close())
    zooKeeperClient = Some (
      AsyncZooKeeperClient(
        servers = servers,
        sessionTimeout = Config.int(s"$config.session-timeout")(),
        connectTimeout = Config.int(s"$config.connect-timeout")(),
        basePath = "",
        watcher = None,
        eCtx = actorSystem.dispatcher
      )
    )
  }

  override def preStart() = initClient()

  override def postStop() = zooKeeperClient.map(_.close)

}