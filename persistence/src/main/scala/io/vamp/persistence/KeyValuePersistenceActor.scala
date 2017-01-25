package io.vamp.persistence

import akka.pattern.ask
import io.vamp.common.akka.IoC
import io.vamp.common.config.Config
import io.vamp.common.spi.ClassMapper
import io.vamp.model.artifact._

import scala.concurrent.Future

class KeyValuePersistenceActorMapper extends ClassMapper {
  val name = "key-value"
  val clazz = classOf[KeyValuePersistenceActor]
}

object KeyValuePersistenceActor {
  val root = "persistence"
  val caching = Config.boolean("vamp.persistence.database.key-value.caching")
}

class KeyValuePersistenceActor extends PersistenceActor with PersistenceMarshaller with ArtifactCaching {

  private val caching = KeyValuePersistenceActor.caching()

  protected def info(): Future[Any] = Future.successful(Map("type" → "key-value", "caching" → caching))

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope] = {
    cacheAll(caching)(`type`, page, perPage) { () ⇒
      val as = type2string(`type`)
      log.debug(s"${getClass.getSimpleName}: all [$as] of $page per $perPage")

      checked[List[String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.All(path(as))) flatMap { list ⇒
        val from = (page - 1) * perPage
        if (from < 0 || from >= list.size) {
          Future.successful(ArtifactResponseEnvelope(Nil, 0, page, perPage))
        }
        else {
          val until = if (from + perPage >= list.size) list.size else from + perPage
          Future.sequence {
            list.slice(from, until).map {
              name ⇒ checked[Option[String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(path(as, name)))
            }
          } map {
            artifacts ⇒ ArtifactResponseEnvelope(artifacts.flatten.flatMap(unmarshall(as, _)), list.size, page, perPage)
          }
        }
      }
    }
  }

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]] = {
    cacheGet(caching)(name, `type`) { () ⇒
      val as = type2string(`type`)
      log.debug(s"${getClass.getSimpleName}: read [$as] - $name}")

      checked[Option[String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(path(as, name))) map {
        case Some(response) ⇒ unmarshall(as, response)
        case _              ⇒ None
      }
    }
  }

  protected def set(artifact: Artifact): Future[Artifact] = {
    cacheSet(caching)(artifact) { () ⇒
      val json = marshall(artifact)
      val as = type2string(artifact.getClass)
      log.debug(s"${getClass.getSimpleName}: set [$as] - $json")

      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(path(as, artifact.name), Option(json)) map (_ ⇒ artifact)
    }
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean] = {
    cacheDelete(caching)(name, `type`) { () ⇒
      val as = type2string(`type`)
      log.debug(s"${getClass.getSimpleName}: delete [$as] - $name}")

      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(path(as, name), None) map (_ ⇒ true)
    }
  }

  private def path(keys: String*) = KeyValuePersistenceActor.root +: keys.toList
}
