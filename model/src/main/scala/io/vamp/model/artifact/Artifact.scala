package io.vamp.model.artifact

trait Artifact {
  def name: String
}

trait Reference extends Artifact

trait Type {
  def `type`: String
}

