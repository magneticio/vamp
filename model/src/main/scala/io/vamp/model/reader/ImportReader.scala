package io.vamp.model.reader

import io.vamp.model.notification.{ EmptyImportError, ImportDefinitionError }
import io.vamp.model.reader.YamlSourceReader._

case class Import(base: Map[String, Any], references: List[ImportReference])

case class ImportReference(name: String, kind: String) {
  override def toString = s"$kind/$name"
}

object ImportReader extends YamlReader[Import] {

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {
    expandToList("import")
    source
  }

  override protected def parse(implicit source: YamlSourceReader): Import = {
    val references = <<?[Any]("import") match {
      case None ⇒ Nil
      case Some(list: List[_]) ⇒
        list.map {
          case str: String if str.isEmpty ⇒ throwException(EmptyImportError)
          case str: String ⇒
            str.split('/').toList match {
              case s :: Nil      ⇒ ImportReference(s, "templates")
              case k :: s :: Nil ⇒ ImportReference(s, k)
              case _             ⇒ throwException(ImportDefinitionError)
            }
          case _ ⇒ throwException(ImportDefinitionError)
        }
      case Some(_) ⇒ throwException(ImportDefinitionError)
    }

    Import(source.flatten({ entry ⇒ entry != "import" }), references)
  }
}
