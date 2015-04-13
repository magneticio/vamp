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
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the ports" in {
    BreedReader.read(res("breed/breed2.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080")))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the environment variables and dependencies" in {
    BreedReader.read(res("breed/breed3.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080")))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with value expansion" in {
    BreedReader.read(res("breed/breed4.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080")))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with partially expanded reference dependencies" in {
    BreedReader.read(res("breed/breed5.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with fully expanded reference dependencies" in {
    BreedReader.read(res("breed/breed6.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with embedded dependencies" in {
    BreedReader.read(res("breed/breed7.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), Nil, Nil, Nil, Map())))
    )
  }

  it should "read the YAML source with expanded embedded dependencies" in {
    BreedReader.read(res("breed/breed8.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), Nil, Nil, Nil, Map())))
    )
  }

  it should "read the YAML source with embedded dependencies with dependencies" in {
    BreedReader.read(res("breed/breed9.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080")))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None))),
      'dependencies(Map("db" -> DefaultBreed("mysql-wrapper", Deployable("magneticio/mysql-wrapper:latest"), List(Port("port", None, Some("3006"))), Nil, Nil, Map("mysql" -> BreedReference("mysql")))))
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

  //  it should "fail on missing environment variable values" in {
  //    expectedError[MissingEnvironmentVariableValueError]({
  //      BreedReader.read(res("breed/breed12.yml"))
  //    }) should have(
  //      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("port", None, None, Trait.Direction.Out)), Map())),
  //      'environmentVariable(EnvironmentVariable("port", None, None, Trait.Direction.Out))
  //    )
  //  }

  //  it should "fail on non unique port name" in {
  //    expectedError[NonUniquePortNameError]({
  //      BreedReader.read(res("breed/breed13.yml"))
  //    }) should have(
  //      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(HttpPort("port", None, Some(80), Trait.Direction.Out), HttpPort("port", None, Some(8080), Trait.Direction.Out)), List(), Map())),
  //      'port(HttpPort("port", None, Some(80), Trait.Direction.Out))
  //    )
  //  }

  //  it should "fail on non unique environment variable name" in {
  //    expectedError[NonUniqueEnvironmentVariableNameError]({
  //      BreedReader.read(res("breed/breed14.yml"))
  //    }) should have(
  //      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("port", None, Some("80/http"), Trait.Direction.Out), EnvironmentVariable("port", None, Some("8080/http"), Trait.Direction.Out)), Map())),
  //      'environmentVariable(EnvironmentVariable("port", None, Some("80/http"), Trait.Direction.Out))
  //    )
  //  }
  //
  //  it should "fail on unresolved dependency reference" in {
  //    expectedError[UnresolvedDependencyForTraitError]({
  //      BreedReader.read(res("breed/breed15.yml"))
  //    }) should have(
  //      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("es.ports.port", None, None, Trait.Direction.In)), Map("db" -> BreedReference("mysql")))),
  //      'name(Trait.Name.asName("es.ports.port"))
  //    )
  //  }

  //  it should "fail on missing dependency environment variable" in {
  //    expectedError[UnresolvedDependencyForTraitError]({
  //      BreedReader.read(res("breed/breed16.yml"))
  //    }) should have(
  //      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("db.ports.web", None, None, Trait.Direction.In)), Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), List(), List(), Map())))),
  //      'name(Trait.Name.asName("db.ports.web"))
  //    )
  //  }
  //
  //  it should "fail on missing dependency port" in {
  //    expectedError[UnresolvedDependencyForTraitError]({
  //      BreedReader.read(res("breed/breed17.yml"))
  //    }) should have(
  //      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(TcpPort("db.ports.web", None, None, Trait.Direction.In)), List(), Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), List(), List(), Map())))),
  //      'name(Trait.Name.asName("db.ports.web"))
  //    )
  //  }

  it should "fail on direct recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed/breed18.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(Port("db.ports.web", None, None)), Nil, Nil, Map("db" -> BreedReference("monarch"))))
    )
  }

  it should "fail on indirect recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed/breed19.yml"))
    }) should have(
      'breed(DefaultBreed("monarch2", Deployable("magneticio/monarch2:latest"), Nil, Nil, Nil, Map("es" -> BreedReference("monarch1"))))
    )
  }
}
