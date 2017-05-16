package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.reader.YamlSourceReader._

object TemplateReader extends YamlReader[Template] {

  override protected def parse(implicit source: YamlSourceReader): Template = {
    Template(name, metadata, first[Any]("definition", "def") match {
      case Some(ds: YamlSourceReader) ⇒ ds.flatten()
      case _                          ⇒ Map()
    })
  }
}
