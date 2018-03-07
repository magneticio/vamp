package io.vamp.persistence

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.persistence.notification.UnsupportedPersistenceRequest

trait TypeOfArtifact {
  this: NotificationProvider ⇒

  protected def type2string(`type`: Class[_]): String = `type` match {
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ Gateway.kind
    case t if classOf[Deployment].isAssignableFrom(t) ⇒ Deployment.kind
    case t if classOf[Breed].isAssignableFrom(t) ⇒ Breed.kind
    case t if classOf[Blueprint].isAssignableFrom(t) ⇒ Blueprint.kind
    case t if classOf[Sla].isAssignableFrom(t) ⇒ Sla.kind
    case t if classOf[Scale].isAssignableFrom(t) ⇒ Scale.kind
    case t if classOf[Escalation].isAssignableFrom(t) ⇒ Escalation.kind
    case t if classOf[Route].isAssignableFrom(t) ⇒ Route.kind
    case t if classOf[Condition].isAssignableFrom(t) ⇒ Condition.kind
    case t if classOf[Rewrite].isAssignableFrom(t) ⇒ Rewrite.kind
    case t if classOf[Workflow].isAssignableFrom(t) ⇒ Workflow.kind
    case t if classOf[Template].isAssignableFrom(t) ⇒ Template.kind
    case _ ⇒ throwException(UnsupportedPersistenceRequest(`type`))
  }
}
