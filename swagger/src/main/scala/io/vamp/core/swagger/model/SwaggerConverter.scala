package io.vamp.core.swagger.model

object SwaggerConverter {

  def asDefinition(any: AnyRef): Definition = {
    Definition(required = List(), properties = Map())
  }
}
