package io.magnetic.vamp_core.persistence.slick.extension

import io.strongtyped.active.slick.models.Identifiable

/**
 * Forces a name attribute on the table definition
 */
trait Nameable[E <: Nameable[E]] extends Identifiable[E] {
  def name: String
}
