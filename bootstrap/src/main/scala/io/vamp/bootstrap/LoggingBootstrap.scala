package io.vamp.bootstrap

import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.Bootstrap
import io.vamp.model.Model
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

import scala.concurrent.Future

abstract class LoggingBootstrap extends Bootstrap {

  def logo: String

  def clazz: Class[_] = classOf[Vamp]

  lazy val version: String = if (Model.version.nonEmpty) s"version ${Model.version}" else ""

  override def start(): Future[Unit] = Future.successful {
    val logger = Logger(LoggerFactory.getLogger(clazz))
    if (!SLF4JBridgeHandler.isInstalled) {
      SLF4JBridgeHandler.removeHandlersForRootLogger()
      SLF4JBridgeHandler.install()
    }
    logger.info(logo)
  }

  override def stop(): Future[Unit] = Future.successful {
    if (SLF4JBridgeHandler.isInstalled) SLF4JBridgeHandler.uninstall()
  }
}
