package io.magnetic.vamp_core.persistence.store

import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.deployment.Deployment
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import io.magnetic.vamp_core.persistence.notification.{ArtifactAlreadyExists, ArtifactNotFound, UnsupportedPersistenceRequest}

import scala.collection.mutable
import scala.concurrent.Future


trait InMemoryStoreProvider extends StoreProvider with OperationNotificationProvider {
  this: ExecutionContextProvider =>

  val store: Store = new InMemoryStore()

  private class InMemoryStore extends Store {

    val store: mutable.Map[String, mutable.Map[String, AnyRef]] = new mutable.HashMap()

    def all(`type`: Class[_]): Future[List[_]] = Future {
      getBranch(`type`) match {
        case Some(branch) => store.get(branch) match {
          case None => Nil
          case Some(map) => map.values.toList
        }
        case None => error(UnsupportedPersistenceRequest(`type`))
      }
    }

    def create(any: AnyRef, ignoreIfExists: Boolean = false): Future[AnyRef] = Future {
      val name = getName(any)
      val artifact = getArtifact(any)
      
      getBranch(any) match {
        case Some(branch) => store.get(branch) match {
          case None =>
            val map = new mutable.HashMap[String, AnyRef]()
            map.put(name, artifact)
            store.put(branch, map)
          case Some(map) => map.get(name) match {
            case None => map.put(name, artifact)
            case Some(_) => if(!ignoreIfExists) error(ArtifactAlreadyExists(name, artifact.getClass))
          }
        }
        case None => error(UnsupportedPersistenceRequest(any.getClass))
      }
      artifact
    }

    def read(name: String, `type`: Class[_]): Future[Option[AnyRef]] = Future {
      getBranch(`type`) match {
        case Some(branch) => store.get(branch) match {
          case None => None
          case Some(map) => map.get(name)
        }
        case None => error(UnsupportedPersistenceRequest(`type`))
      }
    }

    def update(any: AnyRef): Future[AnyRef] = Future {
      val name = getName(any)
      val artifact = getArtifact(any)
      
      getBranch(any) match {
        case Some(branch) => store.get(branch) match {
          case None => error(ArtifactNotFound(name, any.getClass))
          case Some(map) =>
            if (map.get(name).isEmpty)
              error(ArtifactNotFound(name, any.getClass))
            else
              map.put(name, artifact)
        }
        case None => error(UnsupportedPersistenceRequest(any.getClass))
      }
      artifact
    }

    def delete(name: String, `type`: Class[_]): Future[AnyRef] = Future {
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
    case InlineArtifact(_, artifact) => getBranch(artifact.getClass)
    case artifact: Artifact => getBranch(artifact.getClass)
    case _ => error(UnsupportedPersistenceRequest(any.getClass))
  }

  private def getBranch(`type`: Class[_]): Option[String] = `type` match {
    case t if classOf[Breed].isAssignableFrom(t) => Some("breeds")
    case t if classOf[Blueprint].isAssignableFrom(t) => Some("blueprints")
    case t if classOf[Sla].isAssignableFrom(t) => Some("slas")
    case t if classOf[Scale].isAssignableFrom(t) => Some("scales")
    case t if classOf[Escalation].isAssignableFrom(t) => Some("escalations")
    case t if classOf[Routing].isAssignableFrom(t) => Some("routings")
    case t if classOf[Filter].isAssignableFrom(t) => Some("filters")
    case t if classOf[Deployment].isAssignableFrom(t) => Some("deployments")
    case request => None
  }

  private def getName(any: AnyRef): String = any match {
    case InlineArtifact(name, _) => name
    case artifact: Artifact => artifact.name
    case _ => error(UnsupportedPersistenceRequest(any.getClass))
  }

  private def getArtifact(any: AnyRef): AnyRef = any match {
    case InlineArtifact(name, artifact) => artifact
    case artifact: Artifact => artifact
    case _ => error(UnsupportedPersistenceRequest(any.getClass))
  }
}
