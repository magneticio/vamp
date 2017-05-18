package io.vamp.config

import com.typesafe.config.{ Config => TConfig }

import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }
import scala.util.Try
import scala.collection.JavaConverters._
import scala.annotation.implicitNotFound
import shapeless.{ ::, HList, HNil, LabelledGeneric, Lazy, Witness }
import shapeless.labelled._
import cats.syntax.cartesian._
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.data.Validated.{ Invalid, Valid, invalid, valid }

/**
 * Typeclass that provides reading capabilities for each type of config value.
 */
@implicitNotFound(msg = "Cannot find ConfigReader type class for ${A}")
trait ConfigReader[A] {

  def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, A]

  def kind: String

}

object ConfigReader {

  def apply[A](implicit configReader: ConfigReader[A]): ConfigReader[A] = configReader

  def constructConfigReader[A](
    extract: (TConfig, String) => A,
    givenKind: String
  )(implicit configSettings: ConfigSettings): ConfigReader[A] =
    new ConfigReader[A] {
      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, A] =
        Try(extract(configSettings.config, path))
          .fold(_ => invalid(NonEmptyList.of(s"Unable to read config value '$path' of type '$kind'.")), valid)

      override val kind: String = givenKind
    }

  implicit val hnilConfigReader: ConfigReader[HNil] =
    constructConfigReader((_, _) => HNil, "HNil")

  implicit def hlistConfigReader[K <: Symbol, H, T <: HList](
    implicit
    witness: Witness.Aux[K],
    hConfigReader: Lazy[ConfigReader[H]],
    tConfigReader: ConfigReader[T]
  ): ConfigReader[FieldType[K, H] :: T] = {
    val fieldName: String = witness.value.name
    println(s"Current fieldName: $fieldName")
    new ConfigReader[FieldType[K, H] :: T] {
      override def kind: String = "H"

      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, FieldType[K, H] :: T] =
        (hConfigReader.value.read(s"$path.$fieldName") |@| tConfigReader.read(s"$path")).map(field[K](_) :: _)
    }
  }

  implicit def genericConfigReader[A, H <: HList](
    implicit
    generic: LabelledGeneric.Aux[A, H],
    hConfigReader: Lazy[ConfigReader[H]]
  ): ConfigReader[A] =
    new ConfigReader[A] {
      override def kind = "HList"

      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, A] =
        hConfigReader.value.read(path).map(a => generic.from(a))
    }

  implicit val stringConfigReader: ConfigReader[String] =
    constructConfigReader((c, p) => c.getString(p), "String")

  implicit val stringListConfigReader: ConfigReader[List[String]] =
    constructConfigReader((c, p) => c.getStringList(p).asScala.toList, "List[String]")

  implicit val booleanConfigReader: ConfigReader[Boolean] =
    constructConfigReader((c, p) => c.getBoolean(p), "Boolean")

  implicit val booleanListConfigReader: ConfigReader[List[Boolean]] =
    constructConfigReader[List[Boolean]](
      (c, p) =>
        c.getBooleanList(p).asScala.toList.map(_.booleanValue),
      "List[Boolean]"
    )

  implicit val intConfigReader: ConfigReader[Int] =
    constructConfigReader((c, p) => c.getInt(p), "Int")

  implicit val intListConfigReader: ConfigReader[List[Int]] =
    constructConfigReader((c, p) => c.getIntList(p).asScala.toList.map(_.toInt), "List[Int]")

  implicit val longConfigReader: ConfigReader[Long] =
    constructConfigReader((c, p) => c.getLong(p), "Long")

  implicit val longListConfigReader: ConfigReader[List[Long]] =
    constructConfigReader((c, p) => c.getLongList(p).asScala.toList.map(_.toLong), "List[Long]")

  implicit val doubleConfigReader: ConfigReader[Double] =
    constructConfigReader((c, p) => c.getDouble(p), "Double")

  implicit val doubleListConfigReader: ConfigReader[List[Double]] =
    constructConfigReader((c, p) => c.getDoubleList(p).asScala.toList.map(_.toDouble), "List[String]")

  // Make MILLISECONDS configurable
  implicit val durationConfigReader: ConfigReader[FiniteDuration] =
    constructConfigReader((c, p) => FiniteDuration(c.getDuration(p, MILLISECONDS), MILLISECONDS), "FiniteDuration")

  implicit val durationListConfigReader: ConfigReader[List[FiniteDuration]] =
    constructConfigReader(
      (c, p) =>
        c.getDurationList(p, MILLISECONDS).asScala.toList.map(FiniteDuration(_, MILLISECONDS)),
      "List[FiniteDuration]"
    )

  implicit def optionalConfigReader[A](implicit configReader: ConfigReader[A]): ConfigReader[Option[A]] =
    new ConfigReader[Option[A]] {
      override val kind: String = s"Option[${configReader.kind}]"

      // Will always return a Right since its optional!
      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, Option[A]] =
        configReader.read(path) match {
          case Invalid(_) => valid(None)
          case Valid(a) => valid(Some(a))
        }
    }

}
