package io.vamp.core.persistence

import akka.actor.Props
import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.persistence.PersistenceActor._
import io.vamp.core.persistence.notification.PersistenceNotificationProvider

import scala.language.postfixOps

object CachePersistenceActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[CachePersistenceActor], args: _*)
}

case class CachePersistenceInfo(`type`: String, artifacts: Map[String, Map[String, Any]])

class CachePersistenceActor(target: ActorDescription) extends PersistenceReplyActor with CommonSupportForActors with PersistenceNotificationProvider {

  protected def respond(request: Any): Any = request match {

    case Start => actorFor(target) ! Start

    case Shutdown => actorFor(target) ! Shutdown

    case InfoRequest => offload(actorFor(target) ? InfoRequest)

    case All(ofType) => offload(actorFor(target) ? All(ofType))

    case AllPaginated(ofType, page, perPage) => offload(actorFor(target) ? AllPaginated(ofType, page, perPage))

    case Create(artifact, source, ignoreIfExists) => offload(actorFor(target) ? Create(artifact, source, ignoreIfExists))

    case Read(name, ofType) => offload(actorFor(target) ? Read(name, ofType))

    case ReadExpanded(name, ofType) => offload(actorFor(target) ? ReadExpanded(name, ofType))

    case Update(artifact, source, create) => offload(actorFor(target) ? Update(artifact, source, create))

    case Delete(name, ofType) => offload(actorFor(target) ? Delete(name, ofType))

    case _ => error(errorRequest(request))
  }
}
