package io.vamp.operation.config

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.operation.notification._
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object ConfigurationLoaderActor {

  val timeout = KeyValueStoreActor.timeout

  object Reload

  case class Get(`type`: String, flatten: Boolean, key: String = "")

  case class Set(input: String, validateOnly: Boolean)

}

class ConfigurationLoaderActor extends CommonSupportForActors with OperationNotificationProvider {

  import ConfigurationLoaderActor._

  private implicit val timeout = ConfigurationLoaderActor.timeout()

  def receive = {
    case Reload ⇒ reload()
    case g: Get ⇒ reply(get(g.`type`, g.flatten, g.key))
    case s: Set ⇒ reply(set(s.input, s.validateOnly))
    case _      ⇒
  }

  override def preStart() = {
    if (Config.boolean("vamp.operation.reload-configuration")())
      context.system.scheduler.scheduleOnce(Duration.Zero, self, Reload)
  }

  private def get(`type`: String, flatten: Boolean, key: String) = Future.successful {
    Config.export(
      Try(Config.Type.withName(`type`)).getOrElse(Config.Type.applied),
      flatten, { entry ⇒ entry.startsWith("vamp.") && (key.isEmpty || entry == key) }
    )
  }

  private def set(input: String, validateOnly: Boolean): Future[_] = try {
    val config: Map[String, Any] = if (input.trim.isEmpty) Map() else Config.unmarshall(input.trim)
    if (validateOnly) Future.successful(config)
    else {
      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set("configuration" :: Nil, Option(Config.marshall(config))) map { _ ⇒
        Config.load(config)
        reboot()
      }
    }
  }
  catch {
    case _: Exception ⇒ throwException(InvalidConfigurationError)
  }

  private def reload() = {
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get("configuration" :: Nil) map {
      case Some(content: String) if content != Config.marshall(Config.export(Config.Type.dynamic)) ⇒
        Config.load(Config.unmarshall(content))
        reboot()
      case _ ⇒
    }
  }

  private def reboot() = actorSystem.actorSelection("/user/vamp") ! "reload"
}
