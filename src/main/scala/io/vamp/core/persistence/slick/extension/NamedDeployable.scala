package io.vamp.core.persistence.slick.extension

trait NamedDeployable[E <: NamedDeployable[E]] extends Nameable[E] {
  def deploymentId: Option[Int]
}
