package io.magnetic.vamp_core.persistence.slick.extension

/**
 * Table with a name which can be made anonymous
 */
trait AnonymousDeployable[E <: AnonymousDeployable[E]] extends NamedDeployable[E] {

  def isAnonymous: Boolean

  def withAnonymousName :  E
}
