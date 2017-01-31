package io.vamp.model.artifact

import io.vamp.common.crypto.Hash

object Artifact {

  val version = "v1"

  val kind = "kind"

  val metadata = "metadata"
}

trait Artifact {
  def name: String

  def kind: String

  def metadata: Map[String, Any]
}

trait Reference extends Artifact {
  val metadata = Map[String, Any]()
}

trait Type {
  def `type`: String
}

object Lookup {
  val entry = "lookup_name"
}

trait Lookup extends Artifact {

  def lookupName = lookup(name)

  def lookup(string: String) = Hash.hexSha1(s"$getClass@$string", Artifact.version)
}
