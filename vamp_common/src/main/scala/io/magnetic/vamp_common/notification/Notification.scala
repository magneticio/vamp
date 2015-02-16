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

  protected case class Message(parts: Seq[String], args: Seq[String])

  private val logger = Logger(LoggerFactory.getLogger(Notification.getClass))
  private val messages = new mutable.LinkedHashMap[String, mutable.Map[String, Any]]()

  def info(notification: Notification) = logger.info(resolveMessage(notification))

  def error(notification: Notification) = {
    val message = resolveMessage(notification)
    logger.error(message)
    throw new NotificationErrorException(message)
  }

  protected def resolveMessage(implicit notification: Notification): String = {
    try {
      val name = notification.getClass.getSimpleName
      val messageSource = resolveMessageSource

      messageSource.get(name) match {
        case None => defaultMapping
        case Some(value: Message) => resolveValue(value)
        case Some(value: Any) =>
          val message = parseMessage(value.toString)
          messageSource.put(name, message)
          resolveValue(message)
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

  protected def resolveMessageSource(implicit notification: Notification): mutable.Map[String, Any] = {
    val packageName = notification.getClass.getPackage.toString
    messages.get(packageName) match {
      case None =>
        val reader = Source.fromURL(notification.getClass.getResource("messages.yml")).bufferedReader()
        try {
          val input = new Yaml().load(reader).asInstanceOf[java.util.Map[String, Any]].asScala
          messages.put(packageName, input)
          input
        } finally {
          reader.close()
        }
      case Some(map) => map
    }
  }

  protected def parseMessage(message: String)(implicit notification: Notification): Message = {
    val pattern = "\\{[^}]+\\}" r
    val parts = pattern split message
    val args = (pattern findAllIn message).map(s => s.substring(1, s.length - 1)).toList
    Message(parts, args)
  }

  protected def resolveValue(message: Message)(implicit notification: Notification): String = {
    val pi = message.parts.iterator
    val ai = message.args.iterator
    val sb = new StringBuilder()
    while (ai.hasNext) {
      sb append pi.next
      sb append notification.getClass.getDeclaredMethod(ai.next()).invoke(notification).toString
    }
    if (pi.hasNext) sb append pi.next
    sb.toString()
  }
}
