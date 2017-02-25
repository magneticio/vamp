package io.vamp.bootstrap

import com.typesafe.config.ConfigFactory
import io.vamp.common.Namespace

trait Vamp extends App {

  private implicit val namespace: Namespace = ConfigFactory.load().getString("vamp.namespace")

  private def bootstrap = {
    List() :+
      new LoggingBootstrap :+
      new KamonBootstrap :+
      new ConfigurationBootstrap :+
      new ActorBootstrap
  }

  sys.addShutdownHook {
    bootstrap.reverse.foreach(_.stop())
  }

  bootstrap.foreach(_.start())
}
