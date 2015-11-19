package io.vamp.bootstrap

import akka.actor._
import com.typesafe.scalalogging.Logger
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.dictionary.DictionaryBootstrap
import io.vamp.operation.OperationBootstrap
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap
import io.vamp.rest_api.RestApiBootstrap
import io.vamp.gateway_driver.GatewayDriverBootstrap
import org.slf4j.LoggerFactory

import scala.language.{ implicitConversions, postfixOps }

trait Vamp extends App {

  val version = Option(getClass.getPackage.getImplementationVersion)

  val logger = Logger(LoggerFactory.getLogger(classOf[Vamp]))
  
  implicit val actorSystem = ActorSystem("vamp")

  def bootstrap = {
    List() :+
      PulseBootstrap :+
      PersistenceBootstrap :+
      DictionaryBootstrap :+
      ContainerDriverBootstrap :+
      GatewayDriverBootstrap :+
      OperationBootstrap :+
      RestApiBootstrap
  }

  sys.addShutdownHook {
    bootstrap.foreach(_.shutdown)
    actorSystem.terminate()
  }

  logger.info(
    s"""
      |██╗   ██╗ █████╗ ███╗   ███╗██████╗
      |██║   ██║██╔══██╗████╗ ████║██╔══██╗
      |██║   ██║███████║██╔████╔██║██████╔╝
      |╚██╗ ██╔╝██╔══██║██║╚██╔╝██║██╔═══╝
      | ╚████╔╝ ██║  ██║██║ ╚═╝ ██║██║
      |  ╚═══╝  ╚═╝  ╚═╝╚═╝     ╚═╝╚═╝
      |                       ${if (version.isDefined) s"version ${version.get}" else ""}
      |                       by magnetic.io
      |
    """.stripMargin)

  bootstrap.foreach(_.run)
}
