package io.vamp.http_api

import java.io.FileInputStream
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{ ConnectionContext, Http }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.{ Config, Namespace }
import io.vamp.http_api.ws.WebSocketActor

import scala.concurrent.{ ExecutionContext, Future }

class HttpApiBootstrap extends ActorBootstrap {

  private var binding: Option[ServerBinding] = None

  protected def routes(implicit namespace: Namespace, actorSystem: ActorSystem, materializer: Materializer): Route = new HttpApiRoute().allRoutes

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    IoC.createActor(Props(classOf[WebSocketActor], true, true, 2)).map(_ :: Nil)(actorSystem.dispatcher)
  }

  override def start(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[Unit] = {
    implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher

    super.start.flatMap { _ ⇒

      val (interface, port) = (Config.string("vamp.http-api.interface")(), Config.int("vamp.http-api.port")())
      val sslEnabled = Config.has("vamp.http-api.ssl")(namespace)() && Config.boolean("vamp.http-api.ssl")()

      lazy val validSslConfig = sslEnabled &&
        Config.has("vamp.http-api.certificate")(namespace)() &&
        new java.io.File(Config.string("vamp.http-api.certificate")()).exists

      sslEnabled match {
        case true if !validSslConfig ⇒
          logger.error("SSL enabled, but invalid configuration (check certificate)")
          binding = None
          Future.successful(())

        case true ⇒
          logger.info(s"Binding: https://$interface:$port")

          val certificatePath = Config.string("vamp.http-api.certificate")()
          val https = httpsContext(certificatePath)

          Http().setDefaultServerHttpContext(https)

          try {
            Http().bindAndHandle(routes, interface, port, connectionContext = https).map { handle ⇒ binding = Option(handle) }
          }
          catch {
            case e: Exception ⇒
              e.printStackTrace()
              Future.failed(e)
          }

        case _ ⇒
          logger.info(s"Binding: http://$interface:$port")
          try {
            Http().bindAndHandle(routes, interface, port).map { handle ⇒ binding = Option(handle) }
          }
          catch {
            case e: Exception ⇒
              e.printStackTrace()
              Future.failed(e)
          }
      }
    }
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[Unit] = Future.successful(())

  override def stop(implicit actorSystem: ActorSystem, namespace: Namespace): Future[Unit] = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    binding.map { server ⇒
      info(s"Unbinding API")
      server.unbind().flatMap { _ ⇒
        Http().shutdownAllConnectionPools()
        super.stop
      }
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
