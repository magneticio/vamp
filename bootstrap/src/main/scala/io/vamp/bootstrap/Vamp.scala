package io.vamp.bootstrap

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.Namespace
import io.vamp.common.akka.Bootstrap
import io.vamp.http_api.HttpApiBootstrap

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

trait Vamp extends VampApp {

  implicit val system: ActorSystem = ActorSystem("vamp")
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(FiniteDuration(ConfigFactory.load().getDuration("vamp.bootstrap.timeout", MILLISECONDS), MILLISECONDS))

  protected lazy val bootstraps = {
    implicit val namespace: Namespace = Namespace(ConfigFactory.load().getString("vamp.namespace"))
    List() :+
      new LoggingBootstrap {
        lazy val logo: String =
          s"""
             |██╗   ██╗ █████╗ ███╗   ███╗██████╗
             |██║   ██║██╔══██╗████╗ ████║██╔══██╗
             |██║   ██║███████║██╔████╔██║██████╔╝
             |╚██╗ ██╔╝██╔══██║██║╚██╔╝██║██╔═══╝
             | ╚████╔╝ ██║  ██║██║ ╚═╝ ██║██║
             |  ╚═══╝  ╚═╝  ╚═╝╚═╝     ╚═╝╚═╝
             |                                    $version
             |                                    by magnetic.io
             |""".stripMargin
      } :+
      new KamonBootstrap :+
      new ConfigurationBootstrap :+
      new ClassProviderActorBootstrap :+
      new ActorBootstrap(new HttpApiBootstrap :: Nil)
  }

  addShutdownBootstrapHook()

  startBootstraps()
}

trait VampApp extends App {

  protected implicit def system: ActorSystem

  protected implicit def executionContext: ExecutionContext

  protected def bootstraps: List[Bootstrap]

  def addShutdownBootstrapHook(): Unit = sys.addShutdownHook {
    val reversed = bootstraps.reverse
    reversed.tail.foldLeft[Future[Unit]](reversed.head.stop())((f, b) ⇒ f.flatMap(_ ⇒ b.stop())).map { _ ⇒ system.terminate() }.recover {
      case e: Throwable ⇒ e.printStackTrace()
    }
  }

  def startBootstraps(): Future[Unit] = {
    bootstraps.tail.foldLeft[Future[Unit]](bootstraps.head.start())((f, b) ⇒ f.flatMap(_ ⇒ b.start())).recover {
      case e: Throwable ⇒ e.printStackTrace()
    }
  }
}
