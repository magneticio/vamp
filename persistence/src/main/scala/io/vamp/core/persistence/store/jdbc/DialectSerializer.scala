package io.vamp.core.persistence.store.jdbc

import java.nio.charset.StandardCharsets

import io.vamp.core.model.artifact.Dialect
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

object DialectSerializer {

  implicit val formats = DefaultFormats

  def serialize(dialects: Map[Dialect.Value, Any]): Array[Byte] = write(dialects.map({ case (key, value) =>
    key.toString.toLowerCase -> value
  })).getBytes(StandardCharsets.UTF_8)

  def deserialize(blob: Array[Byte]): Map[Dialect.Value, Any] = {
    val map = read[Any](new String(blob, StandardCharsets.UTF_8)).asInstanceOf[Map[String, Any]]
    Dialect.values.toList.flatMap(dialect => map.get(dialect.toString.toLowerCase) match {
      case None => Nil
      case Some(entry) => (dialect -> entry) :: Nil
    }).toMap
  }
}
