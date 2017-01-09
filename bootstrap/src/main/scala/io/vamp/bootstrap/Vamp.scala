package io.vamp.bootstrap

trait Vamp extends App {

  private def bootstrap = {
    List() :+
      new LoggingBootstrap :+
      new ConfigurationBootstrap :+
      new KamonBootstrap :+
      new ActorBootstrap
  }

  sys.addShutdownHook {
    bootstrap.reverse.foreach(_.shutdown())
  }

  bootstrap.foreach(_.run())
}
