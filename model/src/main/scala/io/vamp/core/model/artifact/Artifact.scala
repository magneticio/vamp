package io.vamp.core.model.artifact

import scala.language.implicitConversions

trait Artifact {
  def name: String
}

trait Reference extends Artifact

trait Type {
  def `type`: String
}

