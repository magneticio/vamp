package io.vamp.persistence.zookeeper

import io.vamp.common.ClassMapper
import io.vamp.persistence.KeyValueStoreActor
import io.vamp.persistence.zookeeper.AsyncResponse.{ ChildrenResponse, DataResponse, FailedAsyncResponse }
import org.apache.zookeeper.KeeperException.Code

import scala.concurrent.Future

class ZooKeeperStoreActorMapper extends ClassMapper {
  val name = "zookeeper"
  val clazz: Class[ZooKeeperStoreActor] = classOf[ZooKeeperStoreActor]
}

object ZooKeeperStoreActor {
  val config = "vamp.persistence.key-value-store.zookeeper"
}

class ZooKeeperStoreActor extends KeyValueStoreActor {

  private lazy val client: ZooKeeperClient = ZooKeeperClient.acquire(ZooKeeperConfig())

  override protected def info(): Future[Any] = client.info()

  override protected def children(path: List[String]): Future[List[String]] = {
    val zookeeperPath = pathToString(path)
    log.debug(s"Zookeeper get children for path: $zookeeperPath")
    client.connection.getChildren(zookeeperPath) recoverWith recoverRetrieval(Nil) map {
      case node: ChildrenResponse ⇒ node.children.map { child ⇒ (path :+ child).mkString("/") }.toList
      case _                      ⇒ Nil
    }
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    val zookeeperPath = pathToString(path)
    log.debug(s"Zookeeper get value for path $zookeeperPath")
    client.connection.get(zookeeperPath) recoverWith recoverRetrieval(None) map {
      case response: DataResponse ⇒ response.data.map(new String(_))
      case _                      ⇒ None
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = {
    val zookeeperPath = pathToString(path)
    log.info(s"Zookeeper set value for path $zookeeperPath")
    client.connection.get(zookeeperPath) recoverWith {
      case _ ⇒ client.connection.createPath(zookeeperPath)
    } flatMap { _ ⇒
      client.connection.set(zookeeperPath, data.map(_.getBytes)) recoverWith {
        case failure ⇒
          log.error(failure, failure.getMessage)
          Future.failed(failure)
      }
    }
  }

  private def recoverRetrieval[T](default: T): PartialFunction[Throwable, Future[T]] = {
    case failure: FailedAsyncResponse if failure.code == Code.NONODE ⇒ Future.successful(default)
    case _ ⇒
      // something is going wrong with the connection
      log.warning("Reconnecting to Zookeeper ...")
      ZooKeeperClient.reconnect(client.config)
      Future.successful(default)
  }

  override def postStop(): Unit = {
    ZooKeeperClient.release(client.config)
    super.postStop()
  }
}
