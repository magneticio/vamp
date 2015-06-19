package io.vamp.core.operation.workflow

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

class LoggerContext(name: String) {

  private val logger = Logger(LoggerFactory.getLogger(name))

  def trace(any: Any) = logger.trace(messageOf(any))

  def debug(any: Any) = logger.debug(messageOf(any))

  def info(any: Any) = logger.info(messageOf(any))

  def warn(any: Any) = logger.warn(messageOf(any))

  def error(any: Any) = logger.error(messageOf(any))

  def log(any: Any) = info(any)

  @inline private def messageOf(any: Any) = if (any != null) any.toString else ""
}
