package io.magnetic.vamp_core.persistence.slick.model

import io.magnetic.vamp_core.model.artifact.Trait
import io.magnetic.vamp_core.persistence.slick.model.PortType.PortType

import scala.slick.driver.JdbcDriver.simple._


/**
 * Implicit conversions for Slick columns
 */
object Implicits {

  implicit val traitDirectionMapper = MappedColumnType.base[Trait.Direction.Value, String](
  { c => c.toString},
  { s => Trait.Direction.withName(s)}
  )

  implicit val dependencyTypeMapper = MappedColumnType.base[DependencyType.Value, String](
  { c => c.toString},
  { s => DependencyType.withName(s)}
  )

  val portTypeMap = Map (
    PortType.HTTP -> "http",
    PortType.TCP -> "tcp"
  )
  implicit val portTypeColumnTypeMapper = MappedColumnType.base[PortType, String]  (
    portTypeMap, portTypeMap.map(_.swap)
  )
}
