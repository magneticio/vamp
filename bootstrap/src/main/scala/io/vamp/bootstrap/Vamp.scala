package io.vamp.bootstrap

import io.vamp.common.Namespace

trait Vamp extends App {

  private def bootstrap = {
    List() :+
      new LoggingBootstrap :+
      new ConfigurationBootstrap :+
      new KamonBootstrap :+
      new ActorBootstrap()(Namespace.default)
  }

  sys.addShutdownHook {
    bootstrap.reverse.foreach(_.stop())
  }

  bootstrap.foreach(_.start())
}
