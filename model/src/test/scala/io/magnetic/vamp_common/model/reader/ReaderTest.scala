package io.magnetic.vamp_common.model.reader

import io.magnetic.vamp_common.notification.NotificationErrorException
import io.magnetic.vamp_core.model.reader.YamlReader
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

trait ReaderTest {
  def res(path: String): String = Source.fromURL(getClass.getResource(path)).mkString
}

@RunWith(classOf[JUnitRunner])
class YamlReaderTest extends FlatSpec with Matchers with ReaderTest {

  "YamlReader" should "fail on invalid YAML" in {
    (the[NotificationErrorException] thrownBy {
      new YamlReader[Any] {
        override protected def parse(implicit source: YamlObject): Any = None
      }.read(res("invalid1.yml"))
    }).getMessage should startWith("Generic error during YAML parsing: Can't construct a java object for !ios")
  }

  it should "fail on invalid type" in {
    (the[NotificationErrorException] thrownBy {
      new YamlReader[Any] {
        override protected def parse(implicit source: YamlObject): Any = <<![Int]("integer")
      }.read(res("invalid2.yml"))
    }).getMessage should startWith("Can't match type of path 'integer', expected int but not class java.lang.String.")
  }

  it should "fail on unexpected inner element type" in {
    (the[NotificationErrorException] thrownBy {
      new YamlReader[Any] {
        override protected def parse(implicit source: YamlObject): Any = <<![String]("root" :: "nested" :: "next")
      }.read(res("invalid3.yml"))
    }).getMessage should startWith("Can't find a nested element of 'nested', found class java.lang.String")
  }
}
