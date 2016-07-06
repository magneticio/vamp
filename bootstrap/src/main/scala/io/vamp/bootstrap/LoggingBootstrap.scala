package io.vamp.bootstrap

import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.Bootstrap
import io.vamp.model.Model
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

class LoggingBootstrap extends Bootstrap {

  override def run() = {

    val logger = Logger(LoggerFactory.getLogger(classOf[Vamp]))

    if (!SLF4JBridgeHandler.isInstalled) {
      SLF4JBridgeHandler.removeHandlersForRootLogger()
      SLF4JBridgeHandler.install()
    }

    logger.info(
      s"""
         |██╗   ██╗ █████╗ ███╗   ███╗██████╗
         |██║   ██║██╔══██╗████╗ ████║██╔══██╗
         |██║   ██║███████║██╔████╔██║██████╔╝
         |╚██╗ ██╔╝██╔══██║██║╚██╔╝██║██╔═══╝
         | ╚████╔╝ ██║  ██║██║ ╚═╝ ██║██║
         |  ╚═══╝  ╚═╝  ╚═╝╚═╝     ╚═╝╚═╝
         |                       ${if (Model.version.nonEmpty) s"version ${Model.version}" else ""}
         |                       by magnetic.io
         |
    """.stripMargin)
  }

  override def shutdown() = if (SLF4JBridgeHandler.isInstalled) SLF4JBridgeHandler.uninstall()
}
