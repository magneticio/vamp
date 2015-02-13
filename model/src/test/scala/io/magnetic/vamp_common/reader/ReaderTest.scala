package io.magnetic.vamp_common.reader

import scala.io.Source

trait ReaderTest {
  def res(path: String): String = Source.fromURL(getClass.getResource(path)).mkString
}
