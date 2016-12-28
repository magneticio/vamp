package io.vamp.model.resolver

trait TraitNameAliasResolver {

  private val namePattern = "^(.+?)(\\[.+\\])?$".r

  def resolveNameAlias(name: String): (String, Option[String]) = {
    name match {
      case namePattern(n, a, _*) ⇒ n → (if (a == null) None else Some(a.substring(1, a.length - 1)))
    }
  }

  def asName(name: String, alias: Option[String]) = alias match {
    case Some(a) ⇒ s"$name[$a]"
    case None    ⇒ name
  }
}
