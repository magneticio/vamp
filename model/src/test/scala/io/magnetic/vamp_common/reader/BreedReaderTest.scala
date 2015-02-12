package io.magnetic.vamp_common.reader

import io.magnetic.vamp_core.model.{Dependency, Deployable, Trait}
import io.magnetic.vamp_core.reader.BreedReader
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.io.Source
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class BreedReaderTest extends FlatSpec with Matchers {

  "BreedReader" should "read the simplest YAML (name/deployable only)" in {
    BreedReader.read(res("input1.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the ports" in {
    BreedReader.read(res("input2.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'ports(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the environment variables and dependencies" in {
    BreedReader.read(res("input3.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out), Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In), Trait("db.port", Some("DB_PORT"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'ports(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'environmentVariables(List(Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In), Trait("db.port", Some("DB_PORT"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'dependencies(Map("db" -> Dependency("mysql")))
    )
  }

  it should "read the YAML source with value expansion" in {
    BreedReader.read(res("input4.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out), Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'ports(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'environmentVariables(List(Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'dependencies(Map("db" -> Dependency("mysql")))
    )
  }

  def res(path: String): String = Source.fromURL(getClass.getResource(path)) mkString
}
