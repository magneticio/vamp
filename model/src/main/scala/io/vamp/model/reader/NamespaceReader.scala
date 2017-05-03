package io.vamp.model.reader

import io.vamp.common.Namespace

object NamespaceReader extends YamlReader[Namespace] {

  override protected def parse(implicit source: YamlSourceReader): Namespace = {
    val config: Map[String, Any] = first[Any](List("configuration", "config", "conf")) match {
      case Some(ds: YamlSourceReader) ⇒ ds.flatten()
      case _                          ⇒ Map()
    }
    Namespace(name, config, metadata)
  }
}