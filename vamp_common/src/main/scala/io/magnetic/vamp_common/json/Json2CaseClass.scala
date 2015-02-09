package io.magnetic.vamp_common.json

import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._
import scala.collection.mutable.Queue

object Json2CaseClass {

  private val newline = "\n\n"

  def generate(`package`: String, rootClassName: String, source: String, exclude: Set[String] = Set()): String = {

    val stack = new Queue[CaseClass]

    addCaseClass(stack, "", rootClassName, new Yaml().load(source).asInstanceOf[java.util.Map[String, AnyRef]].asScala, exclude)

    s"package ${`package`}$newline${stack.map(_.toString).toList.mkString(newline)}"
  }

  private def addCaseClass(stack: Queue[CaseClass], path: String, className: String, template: scala.collection.mutable.Map[String, AnyRef], exclude: Set[String]): Unit = {
    stack += new CaseClass(className.capitalize, template.map {
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
            addCaseClass(stack, fieldPath, name.capitalize, v.asInstanceOf[java.util.Map[String, AnyRef]].asScala, exclude)
            new CaseClassField(name, name.capitalize)
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
  override def toString: String = s"$name: ${`class`}"
}
