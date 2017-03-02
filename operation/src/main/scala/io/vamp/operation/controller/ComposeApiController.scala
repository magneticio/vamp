package io.vamp.operation.controller

import io.vamp.model.reader.ComposeBlueprintReader
import ComposeBlueprintReader.fromDockerCompose
import io.vamp.common.http.HttpApiDirectives

/**
 * Controller for converting a docker-compose to blueprint
 */
trait ComposeApiController { self: HttpApiDirectives ⇒

  def createBlueprintFromCompose(artifact: String, name: String, source: String): Option[String] =
    if (artifact == "docker-compose") {
      val composeBlueprint = fromDockerCompose(name)(source)
      val yamlString = toYaml(composeBlueprint.result)
      val commentsString = composeBlueprint.comments.foldLeft("")((acc, comment) ⇒ acc ++ s"# $comment\n")

      println(commentsString ++ "\n" ++ yamlString)

      Some(commentsString ++ "\n" ++ yamlString)
    }
    else None

}
