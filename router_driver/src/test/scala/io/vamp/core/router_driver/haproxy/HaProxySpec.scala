package io.vamp.core.router_driver.haproxy

import org.scalatest.{ FlatSpec, Matchers }

import scala.io.Source

trait HaProxySpec extends FlatSpec with Matchers {
  protected def res(path: String): String = Source.fromURL(getClass.getResource(path)).mkString
}