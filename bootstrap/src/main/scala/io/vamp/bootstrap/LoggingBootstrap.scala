package io.vamp.bootstrap

import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.Bootstrap
import io.vamp.model.Model
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

abstract class LoggingBootstrap extends Bootstrap {

  def logo: String

  lazy val version = if (Model.version.nonEmpty) s"version ${Model.version}" else ""

  override def start() = {
    val logger = Logger(LoggerFactory.getLogger(classOf[Vamp]))
    if (!SLF4JBridgeHandler.isInstalled) {
      SLF4JBridgeHandler.removeHandlersForRootLogger()
      SLF4JBridgeHandler.install()
    }
    logger.info(logo)
  }

  override def stop() = if (SLF4JBridgeHandler.isInstalled) SLF4JBridgeHandler.uninstall()
}
