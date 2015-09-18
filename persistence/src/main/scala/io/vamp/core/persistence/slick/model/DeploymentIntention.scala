package io.vamp.core.persistence.slick.model

object DeploymentIntention extends Enumeration {
  type DeploymentIntentionType = Value
  val Deploy, Undeploy = Value
}
