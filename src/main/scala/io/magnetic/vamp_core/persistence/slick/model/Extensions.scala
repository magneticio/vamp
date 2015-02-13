package io.magnetic.vamp_core.persistence.slick.model

import scala.slick.driver.H2Driver.simple._
trait Extensions {
  implicit class QueryExtensions[T, E, C[_]] (val q: Query[T, E, C]) {
    def page(no: Int, pageSize: Int = 10): Query[T, E, C] = q.drop((no-1)*pageSize).take(pageSize)
  }
}
