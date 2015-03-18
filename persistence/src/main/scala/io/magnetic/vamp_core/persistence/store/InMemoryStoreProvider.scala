package io.magnetic.vamp_core.persistence.store

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.serialization.{ArtifactSerializationFormat, BlueprintSerializationFormat, BreedSerializationFormat, DeploymentSerializationFormat}
import io.magnetic.vamp_core.persistence.notification.{ArtifactAlreadyExists, ArtifactNotFound, PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import org.json4s.native.Serialization.write
import org.slf4j.LoggerFactory

import scala.collection.mutable



trait InMemoryStoreProvider extends StoreProvider with PersistenceNotificationProvider {
  this: ExecutionContextProvider =>

  private val logger = Logger(LoggerFactory.getLogger(classOf[InMemoryStoreProvider]))
  implicit val formats = ArtifactSerializationFormat(BreedSerializationFormat, BlueprintSerializationFormat, DeploymentSerializationFormat)

  val store: Store = new InMemoryStore()

  private class InMemoryStore extends Store {

    val store: mutable.Map[String, mutable.Map[String, Artifact]] = new mutable.HashMap()

    def all(`type`: Class[_ <: Artifact]): List[Artifact] = {
      logger.trace(s"persistence all [${`type`.getSimpleName}]")
      getBranch(`type`) match {
        case Some(branch) => store.get(branch) match {
          case None => Nil
          case Some(map) => map.values.toList
        }
        case None => error(UnsupportedPersistenceRequest(`type`))
      }
    }


    def create(artifact: Artifact, ignoreIfExists: Boolean = true): Artifact = {
      logger.trace(s"persistence create [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
      getBranch(artifact) match {
        case Some(branch) => store.get(branch) match {
          case None =>
            val map = new mutable.HashMap[String, Artifact]()
            map.put(artifact.name, artifact)
            store.put(branch, map)
          case Some(map) => map.get(artifact.name) match {
            case None => map.put(artifact.name, artifact)
            case Some(_) => if (!ignoreIfExists) error(ArtifactAlreadyExists(artifact.name, artifact.getClass))
          }
        }
        case None => error(UnsupportedPersistenceRequest(artifact.getClass))
      }
      artifact
    }

    def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = {
      logger.trace(s"persistence read [${`type`.getSimpleName}] - $name}")
      getBranch(`type`) match {
        case Some(branch) => store.get(branch) match {
          case None => None
          case Some(map) => map.get(name)
        }
        case None => error(UnsupportedPersistenceRequest(`type`))
      }
    }

    def update(artifact: Artifact, create: Boolean = false): Artifact =  {
      logger.trace(s"persistence update [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
      getBranch(artifact) match {
        case Some(branch) => store.get(branch) match {
          case None => if (create) this.create(artifact) else error(ArtifactNotFound(artifact.name, artifact.getClass))
          case Some(map) =>
            if (map.get(artifact.name).isEmpty)
              if (create) this.create(artifact) else error(ArtifactNotFound(artifact.name, artifact.getClass))
            else
              map.put(artifact.name, artifact)
        }
        case None => if (create) this.create(artifact) else error(UnsupportedPersistenceRequest(artifact.getClass))
      }
      artifact
    }

    def delete(name: String, `type`: Class[_ <: Artifact]): Artifact =  {
      logger.trace(s"persistence delete [${`type`.getSimpleName}] - $name}")
      getBranch(`type`) match {
        case Some(branch) => store.get(branch) match {
          case None => error(ArtifactNotFound(name, `type`))
          case Some(map) =>
            if (map.get(name).isEmpty)
              error(ArtifactNotFound(name, `type`))
            else
              map.remove(name).get
        }
        case None => error(UnsupportedPersistenceRequest(`type`))
      }
    }
  }

  private def getBranch(any: AnyRef): Option[String] = any match {
    case artifact: Artifact => getBranch(artifact.getClass)
    case _ => error(UnsupportedPersistenceRequest(any.getClass))
  }

  private def getBranch(`type`: Class[_]): Option[String] = `type` match {
    case t if classOf[Deployment].isAssignableFrom(t) => Some("deployments")
    case t if classOf[Breed].isAssignableFrom(t) => Some("breeds")
    case t if classOf[Blueprint].isAssignableFrom(t) => Some("blueprints")
    case t if classOf[Sla].isAssignableFrom(t) => Some("slas")
    case t if classOf[Scale].isAssignableFrom(t) => Some("scales")
    case t if classOf[Escalation].isAssignableFrom(t) => Some("escalations")
    case t if classOf[Routing].isAssignableFrom(t) => Some("routings")
    case t if classOf[Filter].isAssignableFrom(t) => Some("filters")
    case request => None
  }
}
