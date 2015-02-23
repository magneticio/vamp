package io.magnetic.vamp_core.model

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

case class InlineArtifact(override val name: String, inline: artifact.Inline) extends Artifact
