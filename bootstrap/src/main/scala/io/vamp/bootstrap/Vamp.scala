package io.vamp.bootstrap

import scala.language.{ implicitConversions, postfixOps }

trait Vamp extends App {

  private def bootstrap = {
    List() :+
      new LoggingBootstrap :+
      new KamonBootstrap :+
      new ActorBootstrap
  }

  sys.addShutdownHook {
    bootstrap.reverse.foreach(_.shutdown())
  }

  bootstrap.foreach(_.run())
}
