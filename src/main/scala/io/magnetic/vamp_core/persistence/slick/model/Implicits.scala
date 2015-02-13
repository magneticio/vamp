package io.magnetic.vamp_core.persistence.slick.model

import io.magnetic.vamp_core.model.Trait

import scala.slick.driver.JdbcDriver.simple._


/**
 * Created by lazycoder on 13/02/15.
 */
object Implicits {
  implicit val traitTypeMapper = MappedColumnType.base[Trait.Type.Value, String](
    { c => c.toString },
    { s => Trait.Type.withName(s)}
  )

  implicit val traitPortMapper = MappedColumnType.base[Trait.Port.Value, String](
    { c => c.toString },
    { s => Trait.Port.withName(s)}
  )

  implicit val traitDirectionMapper = MappedColumnType.base[Trait.Direction.Value, String](
    { c => c.toString },
    { s => Trait.Direction.withName(s)}
  )

  implicit val dependencyTypeMapper = MappedColumnType.base[DependencyType.Value, String](
    { c => c.toString },
    { s => DependencyType.withName(s)}
  )
}
