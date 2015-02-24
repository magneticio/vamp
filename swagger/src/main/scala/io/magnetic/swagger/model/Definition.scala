package io.magnetic.swagger.model

import io.magnetic.swagger.Property

case class Definition(required: List[String], properties: Map[String, Property])
