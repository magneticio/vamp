package io.vamp.common.util

import scala.collection.JavaConverters._
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._

object ObjectUtil {

  def isPrimitive(any: Any) = any match {
    case _: Boolean ⇒ true
    case _: Byte    ⇒ true
    case _: Char    ⇒ true
    case _: Short   ⇒ true
    case _: Int     ⇒ true
    case _: Long    ⇒ true
    case _: Float   ⇒ true
    case _: Double  ⇒ true
    case _: String  ⇒ true
    case _          ⇒ false
  }

  def unwrap: Any ⇒ Any = {
    case p if isPrimitive(p)  ⇒ p
    case e: Enumeration#Value ⇒ e
    case l: List[_]           ⇒ l.map(unwrap)
    case m: Map[_, _]         ⇒ m.map { case (k, v) ⇒ k → unwrap(v) }
    case null                 ⇒ None
    case Some(s)              ⇒ Option(unwrap(s))
    case None                 ⇒ None
    case any ⇒
      val reflection = currentMirror.reflect(any)
      currentMirror.reflect(any).symbol.typeSignature.members.toList
        .collect { case s: TermSymbol if !s.isMethod ⇒ reflection.reflectField(s) }
        .map(r ⇒ r.symbol.name.toString.trim → unwrap(r.get))
        .toMap
  }

  def java: Any ⇒ Any = {
    case l: List[_]   ⇒ l.map(java).asJava
    case m: Map[_, _] ⇒ m.map({ case (k, v) ⇒ k → java(v) }).asJava
    case Some(s)      ⇒ Option(java(s))
    case any          ⇒ any
  }
}
