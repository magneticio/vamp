package io.vamp.config

import com.typesafe.config.{ ConfigValueFactory, Config ⇒ TConfig }

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.collection.JavaConverters._
import scala.annotation.implicitNotFound
import shapeless.{ :+:, ::, CNil, Coproduct, HList, HNil, Inl, Inr, LabelledGeneric, Lazy, Witness }
import shapeless.labelled._
import cats.syntax.cartesian._
import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import cats.data.Validated.{ Invalid, Valid, invalid, valid }

/**
 * Typeclass that provides reading capabilities for each type of config value.
 */
@implicitNotFound(msg = "Cannot find ConfigReader instance for ${A}")
trait ConfigReader[A] {

  /**
   * Read a config value resulting in a either a Valid A or NonEmptyList of String error messages.
   */
  def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, A]

  /**
   * Type of the config reader in String representation (used in the error messages).
   */
  def kind: String

}

object ConfigReader {

  /**
   * Constructor for ConfigReader using an implicit ConfigReader of type A provided by genericConfigReader
   */
  def apply[A](implicit configReader: ConfigReader[A]): ConfigReader[A] = configReader

  /**
   * Smart constructor for new ConfigReaders
   */
  def constructConfigReader[A](
    extract:   (TConfig, String) ⇒ A,
    givenKind: String
  )(implicit configSettings: ConfigSettings): ConfigReader[A] =
    new ConfigReader[A] {
      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, A] =
        Try(extract(configSettings.config, path))
          .fold(_ ⇒ invalid(NonEmptyList.of(s"Unable to read config value '$path' of type '$kind'.")), valid)

      override val kind: String = givenKind
    }

  /**
   * ConfigReader instance for HNil for automatic derivation.
   */
  implicit val hnilConfigReader: ConfigReader[HNil] =
    constructConfigReader((_, _) ⇒ HNil, "HNil")

  /**
   * ConfigReader instance for HList for automatic derivation.
   */
  implicit def hlistConfigReader[K <: Symbol, H, T <: HList](
    implicit
    witness:       Witness.Aux[K],
    hConfigReader: Lazy[ConfigReader[H]],
    tConfigReader: ConfigReader[T]
  ): ConfigReader[FieldType[K, H] :: T] =
    new ConfigReader[FieldType[K, H] :: T] {

      val fieldName: String = witness.value.name

      override val kind: String = "H"

      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, FieldType[K, H] :: T] =
        (hConfigReader.value.read(s"$path.${toDashed(fieldName)}") |@| tConfigReader.read(s"$path"))
          .map(field[K](_) :: _)
    }

  private def toDashed(fieldName: String)(implicit configSettings: ConfigSettings): String =
    fieldName.foldLeft("") {
      case (acc, c) if c.isUpper ⇒ acc + configSettings.separator + c.toLower
      case (acc, c)              ⇒ acc + c
    }

  /**
   * ConfigReader instance for CNil.
   * If this gets reached it means that no succesfull parse occurred in the Coproduct of the configuration value(s),
   * returning an ErrorMessage explaining that the config value could not be read on the given path.
   */
  implicit val cnilConfigReader: ConfigReader[CNil] = new ConfigReader[CNil] {

    override val kind = "CNil"

    override def read(path: String)(implicit configSettings: ConfigSettings): Validated[NonEmptyList[String], CNil] =
      invalid(NonEmptyList.of(s"Unable to read config value on path: `$path`."))
  }

  /**
   * ConfigReader instance for Coproduct.
   * This enabled generic derrivation of ADTs of different possible config values.
   * Errors get accumulated through the leftMap on the Invalid case match.
   */
  implicit def coproductConfigReader[K <: Symbol, H, T <: Coproduct](implicit
    witness: Witness.Aux[K],
                                                                     hConfigReader: Lazy[ConfigReader[H]],
                                                                     tConfigReader: ConfigReader[T]): ConfigReader[FieldType[K, H] :+: T] =
    new ConfigReader[FieldType[K, H] :+: T] {

      override val kind: String = witness.value.name

      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, FieldType[K, H] :+: T] = {
        val dashedKind = toDashedType(kind)

        hConfigReader.value.read(s"$path.$dashedKind") match {
          case Valid(a)   ⇒ valid(Inl(field[K](a)))
          case Invalid(e) ⇒ tConfigReader.read(s"$path").map(t ⇒ Inr(t)).leftMap(_.concat(e))
        }
      }
    }

  private def toDashedType(typeName: String)(implicit configSettings: ConfigSettings): String =
    (typeName.take(1).toLowerCase + typeName.drop(1)).foldLeft("") {
      case (acc, c) if c.isUpper ⇒ acc + configSettings.separator + c.toLower
      case (acc, c)              ⇒ acc + c
    }

  implicit def orEnv[A](tryA: Try[A])(key: String)(implicit configReader: ConfigReader[A]): Try[A] =
    tryA.orElse {
      Try(
        sys.env.get(key.replaceAll("[^\\p{L}\\d]", "_").toUpperCase)
        .map(value ⇒ key → ConfigValueFactory.fromAnyRef(value.trim).unwrapped)
        .map(_.asInstanceOf[A]).get
      )
    }

  /**
   * Creates a ConfigReader for any possible A.
   * Transforms from a Generic representation (HList) to a Product A.
   */
  implicit def genericConfigReader[A, H](
    implicit
    generic:       LabelledGeneric.Aux[A, H],
    hConfigReader: Lazy[ConfigReader[H]]
  ): ConfigReader[A] =
    new ConfigReader[A] {
      override val kind = "HList"

      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, A] =
        hConfigReader.value.read(path).map(a ⇒ generic.from(a))
    }

  /**
   * ConfigReader instance for String.
   */
  implicit val stringConfigReader: ConfigReader[String] =
    constructConfigReader((c, p) ⇒ c.getString(p), "String")

  /**
   * ConfigReader instance for List of String.
   * Not that List could not be derrived for any List[A], due to the missing capabilities of the Typesafe Config library.
   */
  implicit val stringListConfigReader: ConfigReader[List[String]] =
    constructConfigReader((c, p) ⇒ c.getStringList(p).asScala.toList, "List[String]")

  /**
   * ConfigReader instance for Boolean.
   */
  implicit val booleanConfigReader: ConfigReader[Boolean] =
    constructConfigReader((c, p) ⇒ c.getBoolean(p), "Boolean")

  /**
   * ConfigReader instance for List of Boolean.
   */
  implicit val booleanListConfigReader: ConfigReader[List[Boolean]] =
    constructConfigReader[List[Boolean]](
      (c, p) ⇒
        c.getBooleanList(p).asScala.toList.map(_.booleanValue),
      "List[Boolean]"
    )

  /**
   * ConfigReader instance for Int.
   */
  implicit val intConfigReader: ConfigReader[Int] =
    constructConfigReader((c, p) ⇒ c.getInt(p), "Int")

  /**
   * ConfigReader instance for List of Int.
   */
  implicit val intListConfigReader: ConfigReader[List[Int]] =
    constructConfigReader((c, p) ⇒ c.getIntList(p).asScala.toList.map(_.toInt), "List[Int]")

  /**
   * ConfigReader instance for Long.
   */
  implicit val longConfigReader: ConfigReader[Long] =
    constructConfigReader((c, p) ⇒ c.getLong(p), "Long")

  /**
   * ConfigReader instance for List of Long.
   */
  implicit val longListConfigReader: ConfigReader[List[Long]] =
    constructConfigReader((c, p) ⇒ c.getLongList(p).asScala.toList.map(_.toLong), "List[Long]")

  /**
   * ConfigReader instance for Double.
   */
  implicit val doubleConfigReader: ConfigReader[Double] =
    constructConfigReader((c, p) ⇒ c.getDouble(p), "Double")

  /**
   * ConfigReader instance for List of Double.
   */
  implicit val doubleListConfigReader: ConfigReader[List[Double]] =
    constructConfigReader((c, p) ⇒ c.getDoubleList(p).asScala.toList.map(_.toDouble), "List[String]")

  /**
   * ConfigReader instance for FiniteDuration.
   */
  implicit val durationConfigReader: ConfigReader[FiniteDuration] =
    new ConfigReader[FiniteDuration] {

      override val kind = "FiniteDuration"

      /**
       * Read a config value resulting in a either a Valid A or NonEmptyList of String error messages.
       */
      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, FiniteDuration] =
        Try(FiniteDuration(configSettings.config.getDuration(path, configSettings.timeUnit), configSettings.timeUnit))
          .fold(_ ⇒ invalid(NonEmptyList.of(s"Unable to read config value '$path' of type '$kind'.")), valid)
    }

  /**
   * ConfigReader instance for List of FiniteDuration.
   */
  implicit val durationListConfigReader: ConfigReader[List[FiniteDuration]] =
    new ConfigReader[List[FiniteDuration]] {

      override val kind = "FiniteDuration"

      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, List[FiniteDuration]] =
        Try(
          configSettings
            .config
            .getDurationList(path, configSettings.timeUnit)
            .asScala.toList
            .map(FiniteDuration(_, configSettings.timeUnit))
        ).fold(_ ⇒ invalid(NonEmptyList.of(s"Unable to read config value '$path' of type '$kind'.")), valid)
    }

  /**
   * ConfigReader instance for Option[A] for any A that has a ConfigReader instance.
   */
  implicit def optionalConfigReader[A](implicit configReader: ConfigReader[A]): ConfigReader[Option[A]] =
    new ConfigReader[Option[A]] {
      override val kind: String = s"Option[${configReader.kind}]"

      // Will always return a Right since its optional!
      override def read(path: String)(implicit configSettings: ConfigSettings): ValidatedNel[String, Option[A]] =
        configReader.read(path) match {
          case Invalid(_) ⇒ valid(None)
          case Valid(a)   ⇒ valid(Some(a))
        }
    }

}
