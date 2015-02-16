package io.magnetic.vamp_common.model.reader

import io.magnetic.vamp_common.notification.NotificationErrorException
import io.magnetic.vamp_core.model.reader.BreedReader
import io.magnetic.vamp_core.model.{BreedReference, DefaultBreed, Deployable, Trait}
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BreedReaderTest extends FlatSpec with Matchers with ReaderTest {

  "BreedReader" should "read the simplest YAML (name/deployable only)" in {
    BreedReader.read(res("breed1.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the ports" in {
    BreedReader.read(res("breed2.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'ports(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the environment variables and dependencies" in {
    BreedReader.read(res("breed3.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out), Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In), Trait("db.port", Some("DB_PORT"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'ports(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'environmentVariables(List(Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In), Trait("db.port", Some("DB_PORT"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with value expansion" in {
    BreedReader.read(res("breed4.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out), Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'ports(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'environmentVariables(List(Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with partially expanded reference dependencies" in {
    BreedReader.read(res("breed5.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with fully expanded reference dependencies" in {
    BreedReader.read(res("breed6.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with embedded dependencies" in {
    BreedReader.read(res("breed7.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), List(), Map())))
    )
  }

  it should "read the YAML source with expanded embedded dependencies" in {
    BreedReader.read(res("breed8.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), List(), Map())))
    )
  }

  it should "read the YAML source with embedded dependencies with dependencies" in {
    BreedReader.read(res("breed9.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out), Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'ports(List(Trait("port", None, Some("8080/http"), Trait.Type.Port, Trait.Direction.Out))),
      'environmentVariables(List(Trait("db.host", Some("DB_HOST"), None, Trait.Type.EnvironmentVariable, Trait.Direction.In))),
      'dependencies(Map("db" -> DefaultBreed("mysql-wrapper", Deployable("magneticio/mysql-wrapper:latest"), List(Trait("port", None, Some("3006/tcp"), Trait.Type.Port, Trait.Direction.Out)), Map("mysql" -> BreedReference("mysql")))))
    )
  }

  it should "fail on no deployable" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed10.yml")) should have message "Can't find any value for path: /deployable"
  }
}
