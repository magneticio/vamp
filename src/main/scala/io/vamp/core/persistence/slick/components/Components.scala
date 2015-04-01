
package io.vamp.core.persistence.slick.components

import com.typesafe.config.ConfigFactory
import io.vamp.core.persistence.slick.extension.VampActiveSlick

import scala.slick.driver.JdbcDriver

class Components(override val jdbcDriver: JdbcDriver)
  extends VampActiveSlick with ModelExtensions

object Components {
  private lazy val driverClz = Class.forName(ConfigFactory.load().getString("vamp.core.model.persistence.slick-driver"))
  private lazy val driverObj: JdbcDriver = driverClz.getField("MODULE$").get(null).asInstanceOf[JdbcDriver]
  lazy val instance = new Components(driverObj)

}
