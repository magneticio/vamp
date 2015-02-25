package io.magnetic.vamp_core.model.artifact

import scala.language.implicitConversions

trait Artifact {
  def name: String
}

trait Reference extends Artifact

trait Type {
  def `type`: String
}

trait Anonymous

package object artifact {
  type Inline = Anonymous
}

case class InlineArtifact(name: String, inline: artifact.Inline) extends Artifact
