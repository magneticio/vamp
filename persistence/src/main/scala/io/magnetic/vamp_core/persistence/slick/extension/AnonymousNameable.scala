package io.magnetic.vamp_core.persistence.slick.extension

/**
 * Table with a name which can be made anonymous
 */
trait AnonymousNameable[E <: AnonymousNameable[E]] extends Nameable[E] {

  def isAnonymous: Boolean

  def withAnonymousName :  E
}
