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
    bootstrap.reverse.foreach(_.stop())
  }

  bootstrap.foreach(_.start())
}
