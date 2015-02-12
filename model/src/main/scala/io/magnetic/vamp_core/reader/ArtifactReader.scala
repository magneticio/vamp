package io.magnetic.vamp_core.reader

import java.io.Reader

import io.magnetic.vamp_core.model.Artifact
import org.yaml.snakeyaml.Yaml

trait ArtifactReader[A <: Artifact] extends YamlReader[A] {

  override protected def read(reader: Reader): A = read(expand(asMap[Any, Any](new Yaml().load(reader))))

  protected def read(input: Map[Any, Any]): A

  protected def name(implicit input: Map[Any, Any]): String = getOrError[String]("name")

  protected def expand(input: Map[Any, Any]): Map[Any, Any] = input

  protected def expand2list(input: Map[Any, Any], path: String): Map[Any, Any] = {
    input
  }
}
