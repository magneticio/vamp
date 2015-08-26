package io.vamp.core.persistence.slick.util

object VampPersistenceUtil {

  def generateAnonymousName: String = "anonymous:" + java.util.UUID.randomUUID.toString

  def restoreToAnonymous(name: String, anonymous: Boolean): String = if (anonymous) "" else name

  def matchesCriteriaForAnonymous(name: String): Boolean = name.isEmpty || name.trim.isEmpty

}