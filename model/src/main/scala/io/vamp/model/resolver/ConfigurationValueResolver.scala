package io.vamp.model.resolver

import io.vamp.common.{ Config, NamespaceProvider }
import io.vamp.model.artifact.{ GlobalReference, ValueReference }

trait ConfigurationValueResolver extends GlobalValueResolver {
  this: NamespaceProvider ⇒

  def valueForReference: PartialFunction[ValueReference, String] = {
    case GlobalReference("conf" | "config" | "configuration", path) ⇒ Config.string(path)()
  }
}
