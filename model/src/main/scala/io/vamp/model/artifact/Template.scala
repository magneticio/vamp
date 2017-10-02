package io.vamp.model.artifact

import io.vamp.common.Artifact

object Template {
  val kind: String = "templates"
}

case class Template(name: String, metadata: Map[String, Any], definition: Map[String, Any]) extends Artifact {
  val kind: String = Template.kind
}
