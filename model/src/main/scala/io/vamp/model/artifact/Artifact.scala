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

trait Lookup {
  this: Artifact ⇒
  def lookupName = Hash.hexSha1(s"$getClass@$name", Artifact.version)
}