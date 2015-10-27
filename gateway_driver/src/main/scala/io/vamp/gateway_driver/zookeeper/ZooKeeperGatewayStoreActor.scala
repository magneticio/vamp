package io.vamp.gateway_driver.zookeeper

import java.io._
import java.net.Socket

import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.model.Gateway
import io.vamp.gateway_driver.notification._
import io.vamp.pulse.notification.PulseFailureNotifier
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.Future
import scala.language.postfixOps

class ZooKeeperGatewayStoreActor extends ZooKeeperServerStatistics with PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

  import io.vamp.gateway_driver.GatewayStore._

  private val definitionPath = "gateways/definition"
  private val rawPath = "gateways/raw"

  private implicit val formats: Formats = DefaultFormats

  private lazy val config = ConfigFactory.load().getConfig("vamp.gateway-driver.zookeeper")

  private lazy val servers = config.getString("servers")

  private lazy val zk = AsyncZooKeeperClient(
    servers = servers,
    sessionTimeout = config.getInt("session-timeout"),
    connectTimeout = config.getInt("connect-timeout"),
    basePath = config.getString("base-path"),
    watcher = None,
    eCtx = actorSystem.dispatcher
  )

  def receive = {
    case Start              ⇒ start()
    case Shutdown           ⇒ shutdown()
    case InfoRequest        ⇒ reply(info)
    case Get                ⇒ reply(get)
    case Put(gateways, raw) ⇒ put(gateways, raw)
    case other              ⇒ unsupported(UnsupportedGatewayStoreRequest(other))
  }

  override def errorNotificationClass = classOf[GatewayStoreResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  private def start() = {
    zk.createPath(definitionPath) onFailure {
      case failure ⇒ log.error(failure, failure.getMessage)
    }
    zk.createPath(rawPath) onFailure {
      case failure ⇒ log.error(failure, failure.getMessage)
    }
  }

  private def shutdown() = zk.close()

  private def info = zkVersion(servers) map {
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

  private def get: Future[List[Gateway]] = zk.get(definitionPath) map {
    case response ⇒ response.data match {
      case Some(data) ⇒ read[List[Gateway]](new String(data))
      case _          ⇒ Nil
    }
  }

  private def put(gateways: List[Gateway], raw: Option[Array[Byte]]) = {
    zk.set(definitionPath, Some(write(gateways).getBytes)) onFailure {
      case failure ⇒ log.error(failure, failure.getMessage)
    }
    zk.set(rawPath, raw) onFailure {
      case failure ⇒ log.error(failure, failure.getMessage)
    }
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