package io.vamp.persistence.slick.extension

trait NamedDeployable[E <: NamedDeployable[E]] extends Nameable[E] {
  def deploymentId: Option[Int]
}
