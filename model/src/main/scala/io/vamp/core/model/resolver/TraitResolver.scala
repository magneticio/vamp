package io.vamp.core.model.resolver

import io.vamp.core.model.artifact._

import scala.language.postfixOps
import scala.util.matching.Regex.Match

trait TraitResolver {

  val marker = '$'

  private val namePattern = "^(.+?)(\\[.+\\])?$".r

  private val referencePattern = """\$\{.+?\}|\$\$|\$\w+\.host|\$\w+\.\w+\.\w+|\$\w+""".r

  def resolveNameAlias(name: String): (String, Option[String]) = {
    name match {
      case namePattern(n, a, _*) => n -> (if (a == null) None else Some(a.substring(1, a.length - 1)))
    }
  }

  def asName(name: String, alias: Option[String]) = alias match {
    case Some(a) => s"$name[$a]"
    case None => name
  }

  def referenceAsPart(reference: ValueReference) = s"$marker{${reference.reference}}"

  def referencesFor(value: String): List[ValueReference] = partsFor(value)._2.filter(_.cluster != s"$marker")

  def resolve(value: String, provider: (ValueReference => String)): String = partsFor(value) match {
    case (fragments, references) =>
      val fi = fragments.iterator
      val ri = references.iterator
      val sb = new StringBuilder()
      while (ri.hasNext) {
        sb append fi.next
        sb append provider(ri.next())
      }
      if (fi.hasNext) sb append fi.next
      sb.toString()
  }

  def partsFor(value: String): (List[String], List[ValueReference]) = {
    val escape = s"$marker$marker"
    val matches = referencePattern findAllMatchIn value toList

    def tailFragments(matches: List[Match]): List[String] = {
      matches match {
        case Nil => Nil
        case head :: Nil => value.substring(head.end) :: Nil
        case head :: tail => value.substring(head.end, tail.head.start) :: tailFragments(tail)
      }
    }

    val fragments = if (matches.nonEmpty) value.substring(0, matches.head.start) :: tailFragments(matches) else value :: Nil

    val references = matches.flatMap { ref =>
      value.substring(ref.start, ref.end) match {
        case s if s == escape => s"$marker" :: Nil
        case s if s.startsWith("${") => s.substring(2, s.length - 1) :: Nil
        case s => s.substring(1) :: Nil
      }
    } map { ref =>
      TraitReference.referenceFor(ref).getOrElse(HostReference.referenceFor(ref).getOrElse(LocalReference(ref)))
    }

    (fragments, references)
  }
}

trait DeploymentTraitResolver extends TraitResolver {

  def resolveEnvironmentVariables(deployment: Deployment, clusters: List[DeploymentCluster]): List[EnvironmentVariable] = {
    deployment.environmentVariables.map(ev => TraitReference.referenceFor(ev.name) match {
      case Some(TraitReference(c, g, n)) if g == TraitReference.groupFor(TraitReference.EnvironmentVariables) && ev.interpolated.isEmpty && ev.value.isDefined && clusters.exists(_.name == c) =>
        ev.copy(alias = None, interpolated = Some(resolve(ev.value.get, valueFor(deployment, None))))
      case _ => ev
    })
  }

  def valueFor(deployment: Deployment, service: Option[DeploymentService])(reference: ValueReference): String = (reference match {
    case ref: TraitReference => deployment.traits.find(_.name == ref.reference).flatMap(_.value)
    case ref: HostReference => deployment.hosts.find(_.name == ref.asTraitReference).flatMap(_.value)
    case ref: LocalReference if ref.name == s"$marker" => Some(s"$marker")
    case ref: LocalReference => service.flatMap(service => (service.environmentVariables ++ service.breed.constants).find(_.name == ref.name).flatMap(_.value))
    case ref => None
  }) getOrElse ""
}
