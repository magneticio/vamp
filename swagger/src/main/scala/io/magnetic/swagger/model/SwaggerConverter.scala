package io.magnetic.swagger.model

object SwaggerConverter {

  def asDefinition(any: AnyRef): Definition = {
    Definition(required = List(), properties = Map())
  }
}
