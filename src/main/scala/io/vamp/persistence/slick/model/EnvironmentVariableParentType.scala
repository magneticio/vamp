package io.vamp.persistence.slick.model

/**
 * EnvironmentVariable parent
 */
object EnvironmentVariableParentType extends Enumeration {
  type EnvironmentVariableParentType = Value
  val Breed, Blueprint, Service, Deployment = Value
}
