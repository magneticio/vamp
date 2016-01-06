package io.vamp.model.artifact

import io.vamp.common.crypto.Hash

object Artifact {
  val version = "v1"
}

trait Artifact {
  def name: String
}

trait Reference extends Artifact

trait Type {
  def `type`: String
}

trait Lookup extends Artifact {

  def lookupName = lookup(name)

  def lookup(string: String) = Hash.hexSha1(s"$getClass@$string", Artifact.version)
}