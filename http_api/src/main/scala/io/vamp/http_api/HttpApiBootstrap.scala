package io.vamp.http_api

import java.io.FileInputStream
import java.security.{ SecureRandom, KeyStore }
import javax.net.ssl.{ SSLContext, TrustManagerFactory, KeyManagerFactory }

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.{ ConnectionContext, Http }
import akka.http.scaladsl.server.Route
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.{ Config, Namespace }
import io.vamp.http_api.ws.WebSocketActor

import scala.concurrent.Future

class HttpApiBootstrap extends ActorBootstrap {

  private var binding: Option[Future[ServerBinding]] = None

  protected def routes(implicit namespace: Namespace, actorSystem: ActorSystem, materializer: Materializer): Route = new HttpApiRoute().allRoutes

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    IoC.createActor(Props(classOf[WebSocketActor], true, true, 2)).map(_ :: Nil)(actorSystem.dispatcher)
  }

  override def start(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {
    super.start

    val (interface, port) = (Config.string("vamp.http-api.interface")(), Config.int("vamp.http-api.port")())

    val sslEnabled = Config.has("vamp.http-api.ssl")(namespace)() &&
      Config.boolean("vamp.http-api.ssl")()

    lazy val validSslConfig =
      Config.has("vamp.http-api.ssl")(namespace)() &&
        Config.has("vamp.http-api.certificate")(namespace)() &&
        new java.io.File(Config.string("vamp.http-api.certificate")()).exists

    implicit lazy val materializer = ActorMaterializer()

    binding = sslEnabled match {
      case true if !validSslConfig ⇒
        logger.error("SSL enabled, but invalid configuration (check certificate)")
        None

      case true ⇒
        logger.info(s"Binding: https://$interface:$port")

        val certificatePath = Config.string("vamp.http-api.certificate")()
        val https = httpsContext(certificatePath)

        Http().setDefaultServerHttpContext(https)
        Option(Http().bindAndHandle(routes, interface, port, connectionContext = https))

      case _ ⇒
        logger.info(s"Binding: http://$interface:$port")

        Option(Http().bindAndHandle(routes, interface, port))
    }
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {}

  override def stop(implicit actorSystem: ActorSystem, namespace: Namespace): Future[Unit] = {
    implicit val executionContext = actorSystem.dispatcher
    binding.map {
      _.flatMap { server ⇒
        info(s"Unbinding API")
        server.unbind().flatMap {
          _ ⇒ Http().shutdownAllConnectionPools()
        }
      }.flatMap { _ ⇒ super.stop }
    } getOrElse super.stop
  }

  private def httpsContext(certificatePath: String) = {
    val password = Array[Char]()
    val keyStore = KeyStore.getInstance("PKCS12")

    val certificate = new FileInputStream(certificatePath)
    keyStore.load(certificate, password)
    certificate.close()

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password)

    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    ConnectionContext.https(sslContext)
  }
}
