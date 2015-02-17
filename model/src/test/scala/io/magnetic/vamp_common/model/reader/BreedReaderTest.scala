package io.magnetic.vamp_common.model.reader

import io.magnetic.vamp_common.notification.NotificationErrorException
import io.magnetic.vamp_core.model._
import io.magnetic.vamp_core.model.reader.BreedReader
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
      'traits(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out))),
      'ports(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the environment variables and dependencies" in {
    BreedReader.read(res("breed3.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out), EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'ports(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with value expansion" in {
    BreedReader.read(res("breed4.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out), EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In))),
      'ports(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In))),
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
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), List(), List(), Map())))
    )
  }

  it should "read the YAML source with expanded embedded dependencies" in {
    BreedReader.read(res("breed8.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), List(), List(), Map())))
    )
  }

  it should "read the YAML source with embedded dependencies with dependencies" in {
    BreedReader.read(res("breed9.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out), EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'ports(List(Port("port", None, Some(Port.Value(Port.Type.Http, 8080)), Trait.Direction.Out))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'dependencies(Map("db" -> DefaultBreed("mysql-wrapper", Deployable("magneticio/mysql-wrapper:latest"), List(Port("port", None, Some(Port.Value(Port.Type.Tcp, 3006)), Trait.Direction.Out)), List(), Map("mysql" -> BreedReference("mysql")))))
    )
  }

  it should "fail on no deployable" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed10.yml")) should have message "Can't find any value for path: /deployable"
  }

  it should "fail on missing port values" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed11.yml")) should have message "Missing port value for 'port' and breed 'monarch -> magneticio/monarch:latest'."
  }

  it should "fail on missing environment variable values" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed12.yml")) should have message "Missing environment variable value for 'port' and breed 'monarch -> magneticio/monarch:latest'."
  }

  it should "fail on non unique port name" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed13.yml")) should have message "Non unique port name 'port' for breed 'monarch -> magneticio/monarch:latest'."
  }

  it should "fail on non unique environment variable name" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed14.yml")) should have message "Non unique environment variable name 'port' for breed 'monarch -> magneticio/monarch:latest'."
  }

  it should "fail on unresolved dependency reference" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed15.yml")) should have message "Dependency reference cannot be resolved for port/environment variable name 'es.ports.port' and breed 'monarch -> magneticio/monarch:latest'."
  }

  it should "fail on missing dependency environment variable" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed16.yml")) should have message "Dependency reference cannot be resolved for port/environment variable name 'db.ports.web' and breed 'monarch -> magneticio/monarch:latest'."
  }

  it should "fail on missing dependency port" in {
    the[NotificationErrorException] thrownBy BreedReader.read(res("breed17.yml")) should have message "Dependency reference cannot be resolved for port/environment variable name 'db.ports.web' and breed 'monarch -> magneticio/monarch:latest'."
  }
}
