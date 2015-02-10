package io.magnetic.vamp_common.json

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.text.Text
import org.apache.commons.cli._
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._
import scala.collection.mutable.Queue
import scala.io.Source

object Json2CaseClass {

  private val logger = Logger(LoggerFactory.getLogger("Json2CaseClass"))

  private val newLine = "\n\n"

  def main(arguments: Array[String]): Unit = {
    val options: Options = new Options
    options.addOption("h", "help", false, "help")
    options.addOption("s", "source", true, "source directory")
    options.addOption("d", "destination", true, "destination directory")
    options.addOption("p", "package", true, "package name")
    val option = new Option("e", "exclude", true, "exclusion filter")
    option.setArgs(Option.UNLIMITED_VALUES)
    options.addOption(option)

    val parser: CommandLineParser = new GnuParser
    val line: CommandLine = parser.parse(options, arguments)

    if (line.hasOption("source") && line.hasOption("destination")) {
      if (line.hasOption("help")) printHelp(options)

      val sourceDirectory = line.getOptionValue("source")
      val destinationDirectory = line.getOptionValue("destination")
      val packageName = if (line.hasOption("package")) line.getOptionValue("package") else ""
      val exclude = if (line.hasOption("exclude")) line.getOptionValues("exclude").toSet else Set[String]()

      generate(packageName, sourceDirectory, destinationDirectory, exclude)
    }
    else printHelp(options)
  }

  private def printHelp(options: Options) = new HelpFormatter().printHelp("Json2CaseClass", options)

  def generate(`package`: String, sourceDirectory: String, destinationDirectory: String, exclude: Set[String] = Set()) = {
    for (file <- new java.io.File(sourceDirectory).listFiles if file.getName endsWith ".json") {

      val fileName = file.getName
      logger.info(s"Processing file: $fileName")

      val rootClassName = fileName.substring(0, fileName.length - ".json".length).capitalize
      val source = Source.fromFile(file).mkString
      val excludeFilter = exclude.filter(_.startsWith(s"$fileName/")).map(_.substring(s"$fileName/".length)).toSet

      val result = buildCaseClass(`package`, rootClassName, source, excludeFilter)
      val outputFileName = s"$destinationDirectory${File.separator}$rootClassName.scala"
      logger.info(s"Writing to file: $outputFileName")
      Files.write(Paths.get(outputFileName), result.getBytes(StandardCharsets.UTF_8))
    }
  }

  def buildCaseClass(`package`: String, rootClassName: String, source: String, exclude: Set[String] = Set()): String = {
    val stack = new Queue[CaseClass]
    addCaseClass(stack, "", Text.toUpperCamelCase(rootClassName), new Yaml().load(source).asInstanceOf[java.util.Map[String, AnyRef]].asScala, exclude)
    s"package ${`package`}$newLine${stack.map(_.toString).toList.mkString(newLine)}"
  }

  private def addCaseClass(stack: Queue[CaseClass], path: String, className: String, template: scala.collection.mutable.Map[String, AnyRef], exclude: Set[String]): Unit = {
    stack += new CaseClass(className, template.map {
      case (name, value) => value match {
        case v: java.lang.String => new CaseClassField(name, "String")
        case v: java.lang.Boolean => new CaseClassField(name, "Boolean")
        case v: java.lang.Integer => new CaseClassField(name, "Int")
        case v: java.lang.Double => new CaseClassField(name, "Double")
        case v: java.util.List[_] => new CaseClassField(name, fieldListType(v.asScala.toList))
        case v: java.util.Map[_, _] =>
          val fieldPath = if (path.isEmpty) name else s"$path/$name"

          if (exclude.contains(fieldPath))
            new CaseClassField(name, "Map[String, AnyRef]")
          else {
            val className = Text.toUpperCamelCase(name.capitalize)
            addCaseClass(stack, fieldPath, className, v.asInstanceOf[java.util.Map[String, AnyRef]].asScala, exclude)
            new CaseClassField(name, className)
          }

        case _ => new CaseClassField(name, "AnyRef")
      }
    }.toList)
  }

  private def fieldListType(list: List[Any]): String = {
    val elementType = if (list.map(_.getClass).toSet.size == 1) list.head match {
      case v: java.lang.Boolean => "Boolean"
      case v: java.lang.Integer => "Int"
      case v: java.lang.Double => "Double"
      case _ => "AnyRef"
    } else "Any"

    s"List[$elementType]"
  }
}

private case class CaseClass(name: String, fields: List[CaseClassField]) {
  override def toString: String = s"case class $name(${fields.map(_.toString).toList.mkString(", ")})"
}

private case class CaseClassField(name: String, `class`: String) {
  override def toString: String = {
    val fieldName = handleReserved(Text.toLowerCamelCase(handleInvalidCharacters(name)))
    s"$fieldName: ${`class`}"
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
