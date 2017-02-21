package io.vamp.model.reader

import java.io.{ File, InputStream, Reader, StringReader }

import io.vamp.common.notification.{ NotificationErrorException, NotificationProvider }
import io.vamp.common.util.{ ObjectUtil, YamlUtil }
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.resolver.TraitNameAliasResolver
import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Formats }
import org.yaml.snakeyaml.error.YAMLException

import scala.io.Source
import scala.language.{ implicitConversions, postfixOps }
import scala.reflect._
import scala.util.Try

sealed trait YamlSource

case class StringSource(string: String) extends YamlSource

case class ReaderSource(reader: Reader) extends YamlSource

case class StreamSource(stream: InputStream) extends YamlSource

case class FileSource(file: File) extends YamlSource

object YamlSource {
  implicit def string2YamlInput(string: String): YamlSource = StringSource(string)

  implicit def reader2YamlInput(reader: Reader): YamlSource = ReaderSource(reader)

  implicit def stream2YamlInput(stream: InputStream): YamlSource = StreamSource(stream)

  implicit def file2YamlInput(file: File): YamlSource = FileSource(file)
}

trait YamlLoader {
  this: NotificationProvider ⇒

  protected def unmarshal(input: YamlSource): Either[YamlSourceReader, List[YamlSourceReader]] = {

    def unmarshal(reader: Reader): Either[YamlSourceReader, List[YamlSourceReader]] = load(reader) match {
      case yaml: YamlSourceReader ⇒ Left(yaml)
      case list: List[_] ⇒ Right(list.map {
        case yaml: YamlSourceReader ⇒ yaml
        case any                    ⇒ error(any, classOf[YamlSourceReader])
      })
      case any ⇒ error(any, classOf[List[YamlSourceReader]])
    }

    input match {
      case ReaderSource(reader) ⇒ unmarshal(reader)
      case StreamSource(stream) ⇒ unmarshal(Source.fromInputStream(stream).bufferedReader())
      case StringSource(string) ⇒ unmarshal(new StringReader(string))
      case FileSource(file)     ⇒ unmarshal(Source.fromFile(file).bufferedReader())
    }
  }

  protected def load(reader: Reader): Any = {

    def flatten(any: Any, acc: List[Any]): List[Any] = any match {
      case l: List[_] ⇒ l.flatMap(flatten(_, acc))
      case other      ⇒ acc :+ other
    }

    val parsed = Try {
      flatten(
        YamlUtil.convert(
          YamlUtil.yaml.loadAll(
            reader
          ), preserveOrder = true
        ), Nil
      )
    } recover {
      case e: Exception ⇒ invalidYaml(e)
    } get

    val result = parsed match {
      case map: collection.Map[_, _] ⇒ YamlSourceReader(map.toMap.asInstanceOf[Map[String, _]])
      case list: List[_] ⇒
        list.map {
          case map: collection.Map[_, _] ⇒ YamlSourceReader(map.toMap.asInstanceOf[Map[String, _]])
          case any                       ⇒ any
        }
      case any ⇒ any
    }

    result
  }

  protected def invalidYaml(e: Exception) = throwException(invalidYamlException(e))

  protected def invalidYamlException(e: Exception) = YamlParsingError(e.getMessage.replaceAll("java object", "resource"), e)

  protected def error(any: Any, expected: Class[_]) = throwException(UnexpectedTypeError("/", expected, if (any != null) any.getClass else classOf[Object]))
}

trait YamlReader[T] extends YamlLoader with ModelNotificationProvider with NameValidator {

  def read(input: YamlSource): T = input match {
    case ReaderSource(reader) ⇒ readSource(reader)
    case StreamSource(stream) ⇒ readSource(Source.fromInputStream(stream).bufferedReader())
    case StringSource(string) ⇒ readSource(new StringReader(string))
    case FileSource(file)     ⇒ readSource(Source.fromFile(file).bufferedReader())
  }

  private def readSource(reader: Reader): T = load(reader, {
    case yaml: YamlSourceReader ⇒ read(yaml)
    case list: List[_] if list.length == 1 && list.head.isInstanceOf[YamlSourceReader] ⇒ read(list.head.asInstanceOf[YamlSourceReader])
    case any ⇒ error(any, classOf[YamlSourceReader])
  })

  protected def load(reader: Reader, process: PartialFunction[Any, T]): T = {
    try {

      def validateConsumed(source: Any, result: T): Unit = source match {
        case yaml: YamlSourceReader ⇒

          if (result.isInstanceOf[Artifact]) {
            yaml.find[String](Artifact.kind)
            yaml.flatten({ _ == Artifact.metadata })
          }
          if (result.isInstanceOf[Lookup]) yaml.find[String](Lookup.entry)

          val nonConsumed = yaml.notConsumed
          if (nonConsumed.nonEmpty) {
            implicit val formats: Formats = DefaultFormats
            throwException(UnexpectedElement(nonConsumed, Serialization.write(nonConsumed)))
          }
        case list: List[_] ⇒ list.foreach(validateConsumed(_, result))
        case _             ⇒
      }

      val source = load(reader)
      val result = process(source)

      validateConsumed(source, result)
      result

    }
    catch {
      case e: NotificationErrorException ⇒ throw e
      case e: YAMLException              ⇒ invalidYaml(e)
    }
    finally {
      reader.close()
    }
  }

  def read(implicit source: YamlSourceReader): T = {

    val expanded = expand(source)
    val parsed = parse(expanded)
    val validated = validate(parsed)

    consistent(validated)
  }

  protected def expand(implicit source: YamlSourceReader): YamlSourceReader = source

  protected def parse(implicit source: YamlSourceReader): T

  protected def validate(any: T): T = any

  protected def consistent(any: T)(implicit source: YamlSourceReader): T = {
    (any, <<?[String](Artifact.kind)) match {
      case (artifact: Artifact, Some(kind)) if kind != artifact.kind ⇒ throwException(InconsistentArtifactKind(kind, artifact.kind))
      case _ ⇒
    }
    any
  }

  protected def <<![V <: Any: ClassTag](path: YamlPath)(implicit source: YamlSourceReader): V = source.get[V](path)

  protected def <<?[V <: Any: ClassTag](path: YamlPath)(implicit source: YamlSourceReader): Option[V] = source.find[V](path)

  protected def <<-(keep: String*)(implicit source: YamlSourceReader): YamlSourceReader = {
    val pull = source.pull({ key ⇒ !keep.contains(key) })
    pull.foreach {
      case (key, _) ⇒ >>(key, None)
    }
    YamlSourceReader(pull)
  }

  protected def >>(path: YamlPath, value: Any)(implicit source: YamlSourceReader): Option[Any] = source.set(path, Option(value))

  protected def >>(path: YamlPath, value: Option[Any])(implicit source: YamlSourceReader): Option[Any] = source.set(path, value)

  protected def first[V <: Any: ClassTag](paths: List[String])(implicit source: YamlSourceReader): Option[V] = first[V](paths.map(string2Path): _*)

  protected def first[V <: Any: ClassTag](paths: YamlPath*)(implicit source: YamlSourceReader): Option[V] = paths.flatMap(<<?[V](_)).headOption

  protected def name(implicit source: YamlSourceReader): String = validateName(<<![String]("name"))

  protected def metadata(implicit source: YamlSourceReader): Map[String, Any] = <<?[Any](Artifact.metadata) match {
    case Some(yaml: YamlSourceReader) ⇒ yaml.flatten()
    case Some(_)                      ⇒ throwException(UnsupportedMetadata)
    case None                         ⇒ Map()
  }

  protected def reference(implicit source: YamlSourceReader): String = validateName(<<?[String]("reference").getOrElse(<<![String]("ref")))

  protected def hasReference(implicit source: YamlSourceReader): Option[String] = <<?[String]("reference").orElse(<<?[String]("ref")) match {
    case Some(ref) ⇒ Option(validateName(ref))
    case None      ⇒ None
  }

  protected def isReference(implicit source: YamlSourceReader): Boolean = hasReference.isDefined

  protected def expandToList(path: YamlPath)(implicit source: YamlSourceReader) = {
    <<?[Any](path) match {
      case None             ⇒
      case Some(_: List[_]) ⇒
      case Some(value)      ⇒ >>(path, List(value))
    }
  }
}

trait NameValidator {
  this: NotificationProvider ⇒

  private val nameMatcher = """^[^\s\/\[\]]+$""".r

  private val strictNameMatcher = """^[^\s\/\[\].]+$""".r

  def validateName(name: String): String = name match {
    case nameMatcher(_*) ⇒ name
    case _               ⇒ throwException(IllegalName(name))
  }

  def validateStrictName(name: String): String =
    name match {
      case strictNameMatcher(_*) ⇒ name
      case _                     ⇒ throwException(IllegalStrictName(name))
    }
}

trait ReferenceYamlReader[T] extends YamlReader[T] {

  def readReference: PartialFunction[Any, T]

  def readReferenceFromSource(any: Any): T = load(new StringReader(any.toString), {
    case list: List[_] if list.size == 1 && list.head.isInstanceOf[YamlSourceReader] ⇒ readReference(list.head)
    case other ⇒ readReference.applyOrElse(other, throwException(UnexpectedInnerElementError("/", classOf[YamlSourceReader])))
  })
}

object AnonymousYamlReader {
  val name = ""
}

trait AnonymousYamlReader[T] extends YamlReader[T] {

  import YamlSourceReader._

  def readAnonymous(any: Any): T = any match {
    case yaml: YamlSourceReader ⇒ read(validateAnonymous(yaml))
  }

  def readOptionalAnonymous(path: YamlPath)(implicit source: YamlSourceReader): Option[T] = <<?[Any](path).flatMap {
    reference ⇒ Some(readAnonymous(reference))
  }

  def validateAnonymous(implicit source: YamlSourceReader): YamlSourceReader = {
    if (!isAnonymous) throwException(NotAnonymousError(name))
    source
  }

  protected override def name(implicit source: YamlSourceReader): String = <<?[String]("name") match {
    case None       ⇒ AnonymousYamlReader.name
    case Some(name) ⇒ validateName(name)
  }

  protected def isAnonymous(implicit source: YamlSourceReader): Boolean = <<?[String]("name").isEmpty
}

trait WeakReferenceYamlReader[T] extends YamlReader[T] with AnonymousYamlReader[T] {

  import YamlSourceReader._

  def readReferenceOrAnonymous(any: Any): T = readReferenceOrAnonymous(any, validateReference = true)

  def readReferenceOrAnonymous(any: Any, validateReference: Boolean): T = any match {
    case string: String                              ⇒ createReference(YamlSourceReader("reference" → string))
    case yaml: YamlSourceReader if validateReference ⇒ read(validateEitherReferenceOrAnonymous(yaml))
    case yaml: YamlSourceReader                      ⇒ read(yaml)
  }

  def readOptionalReferenceOrAnonymous(path: YamlPath, validate: Boolean = true)(implicit source: YamlSourceReader): Option[T] = <<?[Any](path).map {
    reference ⇒ readReferenceOrAnonymous(reference, validate)
  }

  protected def validateEitherReferenceOrAnonymous(implicit source: YamlSourceReader): YamlSourceReader = {
    if (!isAnonymous && !isReference)
      throwException(EitherReferenceOrAnonymous(asReferenceOf, name))
    source
  }

  protected override def name(implicit source: YamlSourceReader): String = <<?[String]("name") match {
    case None        ⇒ AnonymousYamlReader.name
    case Some(value) ⇒ validateName(value)
  }

  override protected def parse(implicit source: YamlSourceReader): T = if (isReference) createReference else createDefault

  protected def `type`(implicit source: YamlSourceReader): String = <<![String]("type")

  protected def parameters(implicit source: YamlSourceReader): Map[String, Any] = source.flatten(_ != "type")

  protected def createReference(implicit source: YamlSourceReader): T

  protected def createDefault(implicit source: YamlSourceReader): T

  protected def asReferenceOf: String = getClass.getSimpleName.substring(0, getClass.getSimpleName.indexOf("Reader")).toLowerCase
}

trait TraitReader extends TraitNameAliasResolver {
  this: YamlReader[_] ⇒

  def parseTraits[A <: Trait](source: Option[YamlSourceReader], mapper: (String, Option[String], Option[String]) ⇒ A, alias: Boolean): List[A] = {
    source match {
      case None ⇒ List[A]()
      case Some(yamlSourceReader: YamlSourceReader) ⇒ yamlSourceReader.pull().map {
        case (name, value) ⇒
          if (value.isInstanceOf[Map[_, _]] || value.isInstanceOf[List[_]])
            throwException(MalformedTraitError(name))

          val (nameValue, aliasValue) = resolveNameAlias(name)
          mapper(nameValue, if (alias) aliasValue else None, if (value == null) None else Some(value.toString))
      } toList
    }
  }

  def ports(name: String = "ports", addGroup: Boolean = false)(implicit source: YamlSourceReader): List[Port] = {
    parseTraits(<<?[YamlSourceReader](name), { (name: String, alias: Option[String], value: Option[String]) ⇒
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) ⇒ ref.asTraitReference(TraitReference.Ports)
          case None      ⇒ name
        }
      }
      else name
      Port(reference, alias, value)
    }, false)
  }

  def environmentVariables(names: List[String] = List("environment_variables", "env"), alias: Boolean = true, addGroup: Boolean = false)(implicit source: YamlSourceReader): List[EnvironmentVariable] = {
    parseTraits(first[YamlSourceReader](names), { (name: String, alias: Option[String], value: Option[String]) ⇒
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) ⇒ ref.asTraitReference(TraitReference.EnvironmentVariables)
          case None      ⇒ name
        }
      }
      else name
      EnvironmentVariable(reference, alias, value)
    }, alias)
  }

  def constants(name: String = "constants", addGroup: Boolean = false)(implicit source: YamlSourceReader): List[Constant] = {
    parseTraits(<<?[YamlSourceReader](name), { (name: String, alias: Option[String], value: Option[String]) ⇒
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) ⇒ ref.asTraitReference(TraitReference.EnvironmentVariables)
          case None      ⇒ name
        }
      }
      else name
      Constant(reference, alias, value)
    }, false)
  }

  def hosts(name: String = "hosts")(implicit source: YamlSourceReader): List[Host] = {
    parseTraits(<<?[YamlSourceReader](name), { (name: String, alias: Option[String], value: Option[String]) ⇒
      Host(TraitReference(name, TraitReference.Hosts, Host.host).reference, value)
    }, false)
  }
}

trait DialectReader {
  this: YamlReader[_] ⇒

  def dialects(implicit source: YamlSourceReader): Map[String, Any] = {
    <<?[Any]("dialects") match {
      case Some(ds: YamlSourceReader) ⇒ ds.flatten()
      case _ ⇒
        <<?[Any]("dialect") match {
          case Some(ds: YamlSourceReader) ⇒ ds.flatten()
          case _                          ⇒ Map()
        }
    }
  }
}

trait ArgumentReader {
  this: YamlReader[_] ⇒

  def expandArguments()(implicit source: YamlSourceReader) = {
    <<?[Any]("arguments") match {
      case None                          ⇒
      case Some(value: List[_])          ⇒
      case Some(value: YamlSourceReader) ⇒ >>("arguments", value.pull().map(YamlSourceReader(_)).toList)
      case Some(value)                   ⇒ >>("arguments", List(value))
    }
  }

  def arguments()(implicit source: YamlSourceReader): List[Argument] = {
    <<?[List[_]]("arguments") match {
      case Some(list) ⇒ list.map {
        case yaml: YamlSourceReader ⇒
          if (yaml.size != 1) throwException(InvalidArgumentError)
          yaml.pull().head match {
            case (key, value) if ObjectUtil.isPrimitive(value) ⇒ Argument(key, value.toString)
            case _ ⇒ throwException(InvalidArgumentError)
          }
        case _ ⇒ throwException(InvalidArgumentError)
      }
      case _ ⇒ Nil
    }
  }

  def validateArguments(argument: List[Argument]) = argument.foreach { argument ⇒
    if (argument.privileged && Try(argument.value.toBoolean).isFailure) throwException(InvalidArgumentValueError(argument))
  }
}

case class ArtifactSource(kind: String, name: String, source: YamlSourceReader) {
  override lazy val toString: String = write(source.flatten())(DefaultFormats)
}

object ArtifactListReader extends YamlReader[List[ArtifactSource]] {

  import YamlSourceReader._

  override def read(input: YamlSource): List[ArtifactSource] = {
    (unmarshal(input) match {
      case Left(item)   ⇒ List(item)
      case Right(items) ⇒ items
    }) map { item ⇒

      val kind = item.get[String]("kind")
      val name = item.get[String]("name")

      ArtifactSource(if (kind.endsWith("s")) kind else s"${kind}s", name, item)
    }
  }

  override protected def parse(implicit source: YamlSourceReader): List[ArtifactSource] = throw new NotImplementedError
}
