package io.vamp.bootstrap

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.Namespace
import io.vamp.http_api.HttpApiBootstrap

import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

trait Vamp extends App {

  protected implicit val system: ActorSystem = ActorSystem("vamp")
  protected implicit val timeout: Timeout = Timeout(FiniteDuration(ConfigFactory.load().getDuration("vamp.bootstrap.timeout", MILLISECONDS), MILLISECONDS))

  protected lazy val bootstrap = {
    implicit val namespace: Namespace = Namespace(ConfigFactory.load().getString("vamp.namespace"))
    List() :+
      new LoggingBootstrap {
        lazy val logo =
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

  sys.addShutdownHook {
    bootstrap.reverse.foreach(_.stop())
    system.terminate()
  }

  bootstrap.foreach(_.start())
}
