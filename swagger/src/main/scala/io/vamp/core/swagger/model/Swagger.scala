package io.vamp.core.swagger.model


case class Contact(email: String)

case class License(name: String, url: String)

case class Info(description: String, version: String, title: String, termsOfService: String, contact: Contact, license: License)

case class Paths()

case class SecurityDefinitions()

case class Swagger(swagger: String, info: Info, host: String, basePath: String, tags: List[Any], schemes: List[String], paths: Paths, securityDefinitions: SecurityDefinitions, definitions: Map[String, Definition])