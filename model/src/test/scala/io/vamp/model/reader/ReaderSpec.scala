package io.vamp.model.reader

import io.vamp.model.notification.{ UnexpectedInnerElementError, UnexpectedTypeError, YamlParsingError }
import io.vamp.common.notification.NotificationErrorException
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

import scala.io.Source
import scala.reflect._
import YamlSourceReader._

trait ReaderSpec extends FlatSpec with Matchers {
  protected def res(path: String): String = Source.fromURL(getClass.getResource(path)).mkString

  protected def expectedError[A <: Any: ClassTag](f: ⇒ Any): A = {
    the[NotificationErrorException] thrownBy f match {
      case NotificationErrorException(error: A, _) ⇒ error
      case unexpected                              ⇒ throw new IllegalArgumentException(s"Expected ${classTag[A].runtimeClass}, actual ${unexpected.notification.getClass}", unexpected)
    }
  }
}

@RunWith(classOf[JUnitRunner])
class YamlReaderSpec extends ReaderSpec {

  "YamlReader" should "fail on invalid YAML" in {
    expectedError[YamlParsingError]({
      new YamlReader[Any] {
        override protected def parse(implicit source: YamlSourceReader): Any = None
      }.read(res("invalid1.yml"))
    }).message should startWith("Can't construct a resource for !ios")
  }

  it should "fail on invalid type" in {
    expectedError[UnexpectedTypeError]({
      new YamlReader[Any] {
        override protected def parse(implicit source: YamlSourceReader): Any = <<![Int]("integer")
      }.read(res("invalid2.yml"))
    }) should have(
      'path("integer"),
      'expected(classOf[Int]),
      'actual(classOf[String])
    )
  }

  it should "fail on unexpected inner element type" in {
    expectedError[UnexpectedInnerElementError]({
      new YamlReader[Any] {
        override protected def parse(implicit source: YamlSourceReader): Any = <<![String]("root" :: "nested" :: "next")
      }.read(res("invalid3.yml"))
    }) should have(
      'path("nested"),
      'found(classOf[String])
    )
  }
}
