package io.vamp.model.resolver

import io.vamp.model.artifact.ValueReference

trait GlobalValueResolver {
  def valueForReference: PartialFunction[ValueReference, String]
}
