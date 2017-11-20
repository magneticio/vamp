package io.vamp.model.artifact

import io.vamp.common.{Artifact, RootAnyMap}

object Template {
  val kind: String = "templates"
}

case class Template(name: String, metadata: RootAnyMap, definition: Map[String, Any]) extends Artifact {
  val kind: String = Template.kind
}
