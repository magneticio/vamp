package io.magnetic.swagger

case class Schema(Ref: String)

case class Parameters(in: String, name: String, description: String, required: Boolean, schema: Schema)

case class Path(tags: List[String], summary: String, description: String, operationId: String, consumes: List[String], produces: List[String], parameters: Parameters, responses: Map[String, Any], security: List[Any])