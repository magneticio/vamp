package io.magnetic.vamp_core.reader

import io.magnetic.vamp_core.model.Artifact

trait ArtifactReader[A <: Artifact] extends YamlReader[A] {

  protected def name(implicit input: YamlSource): String = <<![String]("name")

  protected def expand2list(path: Path)(implicit input: YamlSource) = {
    <<?[Any](path) match {
      case None =>
      case Some(value: List[_]) =>
      case Some(value) => >>(path, List(value))
    }
  }
}
