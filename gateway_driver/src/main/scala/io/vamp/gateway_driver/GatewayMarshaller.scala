package io.vamp.gateway_driver

import io.vamp.model.artifact._

object GatewayMarshaller {

  def name(artifact: Lookup, path: List[String] = Nil): String = path match {
    case Nil ⇒ artifact.name
    case _   ⇒ s"${artifact.name}//${path.mkString("/")}"
  }

  def lookup(artifact: Lookup, path: List[String] = Nil): String = path match {
    case Nil ⇒ artifact.lookupName
    case _   ⇒ artifact.lookup(expand(artifact, path))
  }

  private def expand(artifact: Lookup, path: List[String]): String = (path match {
    case Nil ⇒ expand(artifact.name :: Nil)
    case _   ⇒ expand(artifact.name :: Nil) ++ expand(path)
  }).mkString("/")

  private def expand(path: List[String]): List[String] = path match {
    case some if some.size < 4 ⇒ expand(some :+ "*")
    case _                     ⇒ path
  }
}

trait GatewayMarshaller {

  def info: AnyRef

  def path: List[String]

  def marshall(gateways: List[Gateway]): String
}
