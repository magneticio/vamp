package io.vamp.common.json

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.Logger
import io.vamp.common.text.Text
import org.apache.commons.cli._
import org.json4s.JsonAST.JArray
import org.json4s._
import org.json4s.native.JsonMethods
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.io.Source

object Json2CaseClass {

  private val logger = Logger(LoggerFactory.getLogger(Json2CaseClass.getClass))

  private val newLine = "\n\n"

  def main(arguments: Array[String]): Unit = {
    val options: Options = new Options
    options.addOption("h", "help", false, "help")
    options.addOption("s", "source", true, "source directory")
    options.addOption("d", "destination", true, "destination directory")
    options.addOption("p", "package", true, "package name")

    val excludeOption = new Option("e", "exclude", true, "exclusion filter")
    excludeOption.setArgs(Option.UNLIMITED_VALUES)
    options.addOption(excludeOption)

    val optionalOption = new Option("o", "optional", true, "optional fields")
    optionalOption.setArgs(Option.UNLIMITED_VALUES)
    options.addOption(optionalOption)

    val parser: CommandLineParser = new GnuParser
    val line: CommandLine = parser.parse(options, arguments)

    if (line.hasOption("source") && line.hasOption("destination")) {
      if (line.hasOption("help")) printHelp(options)

      val sourceDirectory = line.getOptionValue("source")
      val destinationDirectory = line.getOptionValue("destination")
      val packageName = if (line.hasOption("package")) line.getOptionValue("package") else ""
      val exclude = if (line.hasOption("exclude")) line.getOptionValues("exclude").toSet else Set[String]()
      val optional = if (line.hasOption("optional")) line.getOptionValues("optional").toSet else Set[String]()

      generate(packageName, sourceDirectory, destinationDirectory, exclude, optional)
    }
    else printHelp(options)
  }

  private def printHelp(options: Options) = new HelpFormatter().printHelp("Json2CaseClass", options)

  def generate(`package`: String, sourceDirectory: String, destinationDirectory: String, exclude: Set[String] = Set(), optional: Set[String] = Set()) = {
    for (file <- new java.io.File(sourceDirectory).listFiles if file.getName endsWith ".json") {

      val fileName = file.getName
      logger.info(s"Processing file: $fileName")

      val rootClassName = fileName.substring(0, fileName.length - ".json".length).capitalize
      val source = Source.fromFile(file).mkString
      val excludeFilter = exclude.filter(_.startsWith(s"$fileName/")).map(_.substring(s"$fileName/".length)).toSet
      val optionalFilter = optional.filter(_.startsWith(s"$fileName/")).map(_.substring(s"$fileName/".length)).toSet

      val result = buildCaseClass(`package`, rootClassName, source, excludeFilter, optionalFilter)
      val outputFileName = s"$destinationDirectory${File.separator}$rootClassName.scala"
      logger.info(s"Writing to file: $outputFileName")
      Files.write(Paths.get(outputFileName), result.getBytes(StandardCharsets.UTF_8))
    }
  }

  def buildCaseClass(`package`: String, rootClassName: String, source: String, exclude: Set[String] = Set(), optional: Set[String] = Set()): String = {
    val stack = new mutable.Queue[CaseClass]
    addCaseClass(stack, "", Text.toUpperCamelCase(rootClassName), JsonMethods.parse(source).asInstanceOf[JObject], exclude, optional)
    s"package ${`package`}$newLine${stack.map(_.toString).toList.mkString(newLine)}"
  }

  private def addCaseClass(stack: mutable.Queue[CaseClass], path: String, className: String, template: JObject, exclude: Set[String], optional: Set[String]): Unit = {

    stack += new CaseClass(className, template.obj.map {
      case JField(name, value) =>
        val field = fieldPath(path, name)
        value match {
          case JNull => new CaseClassField(name, "AnyRef", optional = optional.contains(field))
          case JNothing => new CaseClassField(name, "AnyRef", optional = optional.contains(field))
          case v: JArray => processJArray(v, path, name, exclude, optional, stack)
          case v: JObject => processJObject(v, path, name, exclude, optional, stack)
          case v: JValue => new CaseClassField(name, jValue2ClassName(v), optional = optional.contains(field))
          case _ => new CaseClassField(name, "AnyRef", optional = optional.contains(field))
        }
    }.toList)
  }

  private def processJArray(jArray: JArray, path: String, name: String, exclude: Set[String], optional: Set[String], stack: mutable.Queue[CaseClass]): CaseClassField = {
    val field = fieldPath(path, name)
    if (jArray.arr.map(_.getClass).toSet.size == 1) {
      jArray.arr.head match {
        case v: JObject => processJObject(v, path, name, exclude, optional, stack, list = true)
        case v => new CaseClassField(name, s"List[${jValue2ClassName(v)}]", optional = optional.contains(field))
      }
    } else new CaseClassField(name, s"List[Any]", optional = optional.contains(field))
  }

  private def processJObject(jObject: JObject, path: String, name: String, exclude: Set[String], optional: Set[String], stack: mutable.Queue[CaseClass], list: Boolean = false): CaseClassField = {
    val field = fieldPath(path, name)
    if (exclude.contains(field))
      new CaseClassField(name, if (list) "List[Map[String, AnyRef]]" else "Map[String, AnyRef]", optional = optional.contains(field))
    else {
      val className = Text.toUpperCamelCase(name.capitalize)
      addCaseClass(stack, field, className, jObject, exclude, optional)
      new CaseClassField(name, if (list) s"List[$className]" else className, optional = optional.contains(field))
    }
  }

  private def jValue2ClassName(jValue: JValue): String = jValue.values.getClass.getSimpleName match {
    case "BigInt" => "Int"
    case s => s
  }

  private def fieldPath(path: String, name: String) = if (path.isEmpty) name else s"$path/$name"
}

private case class CaseClass(name: String, fields: List[CaseClassField]) {
  override def toString: String = s"case class $name(${fields.map(_.toString).toList.mkString(", ")})"
}

private case class CaseClassField(name: String, `class`: String, optional: Boolean) {
  override def toString: String = {
    val fieldName = handleReserved(Text.toLowerCamelCase(handleInvalidCharacters(name)))
    if (optional) s"$fieldName: Option[${`class`}]" else s"$fieldName: ${`class`}"
  }

  private def handleInvalidCharacters(s: String): String = {
    val chars = s.toList
    val startWith = if (!Character.isUnicodeIdentifierStart(chars.head)) '_' else chars.head
    chars.tail.foldLeft(startWith.toString)((s1, s2) => {
      val next = if (!Character.isUnicodeIdentifierPart(s2)) '_' else s2
      s1 + next
    })
  }

  private def handleReserved(s: String): String = {
    val st = scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable]
    if (st.nme.keywords.exists(_.toString == name)) s"`$s`" else s
  }
}
