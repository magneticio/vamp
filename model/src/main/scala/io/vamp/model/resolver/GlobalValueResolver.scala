package io.vamp.model.resolver

import io.vamp.common.config.Config
import io.vamp.model.artifact.{ GlobalReference, ValueReference }

object GlobalValueResolver extends GlobalValueResolver with ConfigurationValueResolver {
  override def valueForReference = super[ConfigurationValueResolver].valueForReference
}

trait GlobalValueResolver {
  def valueForReference: PartialFunction[ValueReference, String]
}

trait ConfigurationValueResolver extends GlobalValueResolver {
  def valueForReference: PartialFunction[ValueReference, String] = {
    case GlobalReference("conf" | "config" | "configuration", path) ⇒ Config.string(path)()
  }
}
