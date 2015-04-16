package io.vamp.core.model.reader

import io.vamp.core.model.artifact._
import io.vamp.core.model.notification._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BreedReaderTest extends ReaderTest {

  "BreedReader" should "read the simplest YAML (name/deployable only)" in {
    BreedReader.read(res("breed/breed1.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the ports" in {
    BreedReader.read(res("breed/breed2.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the environment variables and dependencies" in {
    BreedReader.read(res("breed/breed3.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List(EnvironmentVariable("DB_HOST", None, Some("$db.host")), EnvironmentVariable("DB_PORT", None, Some("$db.ports.port")))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with value expansion" in {
    BreedReader.read(res("breed/breed4.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List(EnvironmentVariable("DB_HOST", None, Some("$db.host")))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with partially expanded reference dependencies" in {
    BreedReader.read(res("breed/breed5.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with fully expanded reference dependencies" in {
    BreedReader.read(res("breed/breed6.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with embedded dependencies" in {
    BreedReader.read(res("breed/breed7.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), Nil, Nil, Nil, Map())))
    )
  }

  it should "read the YAML source with expanded embedded dependencies" in {
    BreedReader.read(res("breed/breed8.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), Nil, Nil, Nil, Map())))
    )
  }

  it should "read the YAML source with embedded dependencies with dependencies" in {
    BreedReader.read(res("breed/breed9.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/tcp")))),
      'environmentVariables(List(EnvironmentVariable("DB_HOST", None, Some("$db.host")), EnvironmentVariable("DB_PORT", None, Some("$db.ports.port")))),
      'dependencies(Map("db" -> DefaultBreed("mysql-wrapper", Deployable("magneticio/mysql-wrapper:latest"), List(Port("port", None, Some("3006/tcp"))), Nil, Nil, Map("mysql" -> BreedReference("mysql")))))
    )
  }

  it should "fail on no deployable" in {
    expectedError[MissingPathValueError]({
      BreedReader.read(res("breed/breed10.yml"))
    }) should have(
      'path("deployable")
    )
  }

  it should "fail on missing port values" in {
    expectedError[MissingPortValueError]({
      BreedReader.read(res("breed/breed11.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(Port("port", None, None)), Nil, Nil, Map())),
      'port(Port("port", None, None))
    )
  }

  it should "not fail on missing environment variable values" in {
    BreedReader.read(res("breed/breed12.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List(EnvironmentVariable("port", None, None))),
      'dependencies(Map())
    )
  }

  it should "not fail on non unique port name but should get the last one only" in {
    BreedReader.read(res("breed/breed13.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "not fail on non unique environment variable name but should get the last one only" in {
    BreedReader.read(res("breed/breed14.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List(EnvironmentVariable("port", None, Some("8080/http")))),
      'dependencies(Map())
    )
  }

  it should "fail on unresolved dependency reference" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed15.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), Nil, List(EnvironmentVariable("port", None, Some("$es.ports.port"))), Nil, Map("db" -> BreedReference("mysql")))),
      'reference("es.ports.port")
    )
  }

  it should "fail on missing dependency environment variable" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed16.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), Nil, List(EnvironmentVariable("port", None, Some("$db.ports.web"))), Nil, Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), Nil, Nil, Nil, Map())))),
      'reference("db.ports.web")
    )
  }

  it should "fail on missing dependency port" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed17.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(Port("port", None, Some("$db.ports.web"))), Nil, Nil, Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), Nil, Nil, Nil, Map())))),
      'reference("db.ports.web")
    )
  }

  it should "fail on direct recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed/breed18.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(Port("port", None, Some("$db.ports.web"))), Nil, Nil, Map("db" -> BreedReference("monarch"))))
    )
  }

  it should "fail on indirect recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed/breed19.yml"))
    }) should have(
      'breed(DefaultBreed("monarch2", Deployable("magneticio/monarch2:latest"), Nil, Nil, Nil, Map("es" -> BreedReference("monarch1"))))
    )
  }

  it should "fail on missing constant values" in {
    expectedError[MissingConstantValueError]({
      BreedReader.read(res("breed/breed20.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), Nil, Nil, List(Constant("port", None, None)), Map())),
      'constant(Constant("port", None, None))
    )
  }

  it should "fail on missing dependency constant" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed21.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(Port("port", None, Some("$db.constants.web"))), Nil, Nil, Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), List(Port("web", None, Some("80"))), List(EnvironmentVariable("web", None, Some("80"))), Nil, Map())))),
      'reference("db.constants.web")
    )
  }

  it should "resolve dependency constant" in {
    BreedReader.read(res("breed/breed22.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("web", None, Some("$db.constants.port")))),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), Nil, Nil, List(Constant("port", None, Some("80/tcp"))), Map())))
    )
  }
}
