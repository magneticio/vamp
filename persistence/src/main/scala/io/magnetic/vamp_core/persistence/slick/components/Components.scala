
package io.magnetic.vamp_core.persistence.slick.components

import io.magnetic.vamp_core.persistence.slick.extension.VampActiveSlick

import scala.slick.driver.{H2Driver, JdbcDriver}

class Components(override val jdbcDriver: JdbcDriver)
  extends VampActiveSlick with ModelExtensions

object Components {
  val instance = new Components(H2Driver)
}
