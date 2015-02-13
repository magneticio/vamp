package io.magnetic.vamp_core.persistence.slick.model

import scala.slick.driver.JdbcProfile

/**
 * Created by lazycoder on 13/02/15.
 */
trait DriverComponent {
  val driver: JdbcProfile

}
