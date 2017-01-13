package io.vamp.bootstrap

import akka.actor.{ Actor, ActorSystem, Props }
import akka.contrib.throttle.Throttler.{ SetTarget, _ }
import akka.contrib.throttle.TimerBasedThrottler
import io.vamp.common.akka.{ Bootstrap, ActorBootstrap ⇒ ActorBootstrapService }
import io.vamp.common.config.Config
import io.vamp.common.spi.ClassProvider

import scala.concurrent.{ ExecutionContext, Future }

private object Run

private object Reload

private object Shutdown

class ActorBootstrap extends Bootstrap {

  private implicit lazy val system = ActorSystem("vamp")

  private lazy val bootstrap = ClassProvider.all[ActorBootstrapService].toList

  private lazy val throttler = {
    val throttler = system.actorOf(Props(
      classOf[TimerBasedThrottler],
      1 msgsPer Config.duration("vamp.bootstrap.actor-throttle")()
    ))
    throttler ! SetTarget(Option(system.actorOf(Props(new Actor {
      def receive = {
        case Run      ⇒ bootstrap.foreach(_.run)
        case Reload   ⇒ shutdownActors({ () ⇒ throttler ! Run })
        case Shutdown ⇒ shutdownActors({ () ⇒ system.terminate() })
        case _        ⇒
      }
    }))))
    throttler
  }

  override def run() = throttler ! Run

  override def shutdown() = throttler ! Shutdown

  private def shutdownActors(afterShutdown: () ⇒ Unit) = {
    implicit val executionContext: ExecutionContext = system.dispatcher
    Future.sequence(bootstrap.reverse.map(_.shutdown)).foreach(_ ⇒ afterShutdown())
  }

  system.actorOf(Props(new Actor {
    def receive = {
      case "reload" ⇒ throttler ! Reload
      case _        ⇒
    }
  }), "vamp")
}
