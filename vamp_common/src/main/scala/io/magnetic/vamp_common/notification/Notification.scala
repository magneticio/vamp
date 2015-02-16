package io.magnetic.vamp_common.notification

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.language.postfixOps

trait Notification

class NotificationErrorException(message: String) extends RuntimeException(message)

object Notification {

  private val logger = Logger(LoggerFactory.getLogger(Notification.getClass))
  private val messages = new mutable.LinkedHashMap[String, Map[String, String]]()

  def info(notification: Notification) = logger.info(resolveMessage(notification))

  def error(notification: Notification) = {
    val message = resolveMessage(notification)
    logger.error(message)
    throw new NotificationErrorException(message)
  }

  protected def resolveMessage(implicit notification: Notification): String = {
    try {
      resolveMessageSource.get(notification.getClass.getSimpleName) match {
        case None => defaultMapping
        case Some(value) => resolveValue(value)
      }
    } catch {
      case e: NoSuchMethodException =>
        val field = e.getMessage.substring(e.getMessage.lastIndexOf('.') + 1, e.getMessage.length - 2)
        logger.error(s"Message mapping error: field '$field' not defined for ${notification.getClass}")
        defaultMapping
      case e: Exception =>
        logger.error(e.getMessage, e)
        defaultMapping
    }
  }
  
  protected def defaultMapping(implicit notification: Notification) = s"Message not mapped: ${notification.getClass.getSimpleName}"

  protected def resolveMessageSource(implicit notification: Notification): Map[String, String] = {
    val packageName = notification.getClass.getPackage.toString
    messages.get(packageName) match {
      case None =>
        val reader = Source.fromURL(notification.getClass.getResource("messages.yml")).bufferedReader()
        try {
          val input = new Yaml().load(reader).asInstanceOf[java.util.Map[String, String]].asScala.toMap
          messages.put(packageName, input)
          input
        } finally {
          reader.close()
        }
      case Some(map) => map
    }
  }

  protected def resolveValue(message: String)(implicit notification: Notification): String = {
    val pattern = "\\{[^}]+\\}" r
    val parts = pattern split message
    val args = (pattern findAllIn message).map(s => s.substring(1, s.length - 1)).toList

    val pi = parts.iterator
    val ai = args.iterator
    val sb = new StringBuilder()
    while (ai.hasNext) {
      sb append pi.next
      sb append notification.getClass.getDeclaredMethod(ai.next()).invoke(notification).toString
    }
    if (pi.hasNext) sb append pi.next
    sb.toString()
  }
}
