package io.vamp.core.persistence.slick.util

object VampPersistenceUtil {
  def generatedAnonymousName = "anonymous:" + java.util.UUID.randomUUID.toString

  def restoreToAnonymous(name: String, anonymous: Boolean): String = if (anonymous) "" else name

  def matchesCriteriaForAnonymous(name: String) = name.isEmpty || name.trim.isEmpty

}