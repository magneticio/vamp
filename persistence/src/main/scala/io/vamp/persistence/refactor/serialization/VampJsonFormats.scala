package io.vamp.persistence.refactor.serialization

import io.vamp.common.Id
import io.vamp.model.artifact.EnvironmentVariable
import spray.json._
/**
 * Created by mihai on 11/10/17.
 */
trait VampJsonFormats extends DefaultJsonProtocol {

  implicit val environmentVariableSerilizationSpecifier: SerializationSpecifier[EnvironmentVariable] =
    SerializationSpecifier[EnvironmentVariable](jsonFormat4(EnvironmentVariable), "envVar", (e ⇒ Id[EnvironmentVariable](e.name)))
}

case class SerializationSpecifier[T](format: RootJsonFormat[T], typeName: String, idExtractor: T ⇒ Id[T])
