package io.vamp.core.model.reader

import io.vamp.core.model.artifact._
import io.vamp.core.model.notification._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BlueprintReaderTest extends FlatSpec with Matchers with ReaderTest {

  "BlueprintReader" should "read the simplest YAML (name and single breed only)" in {
    BlueprintReader.read(res("blueprint/blueprint1.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the endpoints and parameters" in {
    BlueprintReader.read(res("blueprint/blueprint2.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(List(Port("notorious.ports.port", None, Some("8080")))),
      'environmentVariables(List(EnvironmentVariable("notorious.environment_variables.aspect", None, Some("thorium"))))
    )
  }

  it should "read the reference sla" in {
    BlueprintReader.read(res("blueprint/blueprint3.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(SlaReference("strong-mountain", Nil))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference sla with explicit name" in {
    BlueprintReader.read(res("blueprint/blueprint4.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(SlaReference("strong-mountain", Nil))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference sla with escalations" in {
    BlueprintReader.read(res("blueprint/blueprint5.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(SlaReference("strong-mountain", List(ToAllEscalation("", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious")))))))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous sla" in {
    BlueprintReader.read(res("blueprint/blueprint6.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(GenericSla("", "vital-cloud", Nil, Map("reborn" -> "red-swallow")))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous sla with escalations" in {
    BlueprintReader.read(res("blueprint/blueprint7.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(GenericSla("", "vital-cloud", List(EscalationReference("red-flag")), Map("reborn" -> "red-swallow")))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous scale" in {
    BlueprintReader.read(res("blueprint/blueprint8.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Some(DefaultScale("", 0.2, 120, 2)), None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference scale" in {
    BlueprintReader.read(res("blueprint/blueprint9.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Some(ScaleReference("large")), None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference scale with explicit name parameter" in {
    BlueprintReader.read(res("blueprint/blueprint10.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Some(ScaleReference("large")), None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference routing" in {
    BlueprintReader.read(res("blueprint/blueprint11.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(RoutingReference("conservative")))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with weight" in {
    BlueprintReader.read(res("blueprint/blueprint12.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", Some(50), Nil)))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with filter reference" in {
    BlueprintReader.read(res("blueprint/blueprint13.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with filter references" in {
    BlueprintReader.read(res("blueprint/blueprint14.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", None, List(FilterReference("android"), FilterReference("ios")))))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with anonymous filter" in {
    BlueprintReader.read(res("blueprint/blueprint15.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", Some(10), List(DefaultFilter("", "user.agent != ios")))))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "fail on both reference and inline routing declarations" in {
    expectedError[EitherReferenceOrAnonymous]({
      BlueprintReader.read(res("blueprint/blueprint16.yml"))
    }) should have(
      'name("routing"),
      'reference("!ios")
    )
  }

  it should "expand the filter list" in {
    BlueprintReader.read(res("blueprint/blueprint17.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the breed" in {
    BlueprintReader.read(res("blueprint/blueprint18.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the services" in {
    BlueprintReader.read(res("blueprint/blueprint19.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the cluster" in {
    BlueprintReader.read(res("blueprint/blueprint20.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the more complex blueprint" in {
    BlueprintReader.read(res("blueprint/blueprint21.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Some(DefaultScale("", 0.2, 120.0, 2)), Some(DefaultRouting("", Some(95), List(DefaultFilter("", "ua = android"))))), Service(BreedReference("remote-venus"), Some(ScaleReference("worthy")), None)), Some(GenericSla("", "vital-cloud", List(ToAllEscalation("", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious"))))), Map("reborn" -> "red-swallow")))), Cluster("notorious", List(Service(DefaultBreed("nocturnal-viper", Deployable("anaconda"), Nil, Nil, Nil, Map()), None, None)), Some(SlaReference("strong-mountain", Nil))), Cluster("needless", List(Service(DefaultBreed("hideous-canal", Deployable("old/crystal"), Nil, Nil, Nil, Map()), None, None)), Some(SlaReference("fish-steamy", Nil))), Cluster("omega", List(Service(BreedReference("scary-lion"), None, None)), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(List(EnvironmentVariable("omega.environment_variables.aspect", None, Some("thorium"))))
    )
  }

  it should "validate endpoints for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint/blueprint22.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), None, None)), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "validate endpoints for inline breeds - no cluster" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint/blueprint23.yml"))
    }) should have(
      'name("omega.ports.port"),
      'value(Some("8080"))
    )
  }

  it should "validate endpoints for inline breeds - not a port" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint/blueprint24.yml"))
    }) should have(
      'name("supersonic.ports.port"),
      'value(Some("8080"))
    )
  }

  it should "validate endpoints for inline breeds - no port" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint/blueprint25.yml"))
    }) should have(
      'name("supersonic.ports.http"),
      'value(Some("8080"))
    )
  }

  it should "validate environment variables for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint/blueprint26.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), Nil, List(EnvironmentVariable("port", None, None)), Nil, Map()), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(List(EnvironmentVariable("supersonic.environment_variables.port", None, Some("8080"))))
    )
  }

  it should "validate environment variables for inline breeds - no cluster" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint27.yml"))
    }) should have(
      'name("omega.environment_variables.port"),
      'value(Some("8080"))
    )
  }

  it should "validate environment variables for inline breeds - not a trait" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint28.yml"))
    }) should have(
      'name("supersonic.environment_variables.port"),
      'value(Some("8080"))
    )
  }

  it should "validate environment variables for inline breeds - no trait" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint29.yml"))
    }) should have(
      'name("supersonic.environment_variables.http"),
      'value(Some("8080"))
    )
  }

  it should "validate environment variables for setting port values" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint30.yml"))
    }) should have(
      'name("supersonic.environment_variables.port"),
      'value(Some("8080"))
    )
  }

  it should "validate breed uniqueness across clusters" in {
    expectedError[NonUniqueBlueprintBreedReferenceError]({
      BlueprintReader.read(res("blueprint/blueprint31.yml"))
    }) should have(
      'name("solid-barbershop")
    )
  }

  it should "validate breed uniqueness across services" in {
    expectedError[NonUniqueBlueprintBreedReferenceError]({
      BlueprintReader.read(res("blueprint/blueprint32.yml"))
    }) should have(
      'name("solid-barbershop")
    )
  }

  it should "validate breed cross dependencies - no inline" in {
    BlueprintReader.read(res("blueprint/blueprint33.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), None, None)), None), Cluster("notorious", List(Service(BreedReference("elastic-search"), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "validate breed cross dependencies - inline and valid" in {
    BlueprintReader.read(res("blueprint/blueprint34.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("solid/barbershop"), Nil, Nil, Nil, Map("es" -> BreedReference("elastic-search"))), None, None)), None), Cluster("notorious", List(Service(BreedReference("elastic-search"), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "validate breed cross dependencies - missing reference for an inline breed" in {
    expectedError[UnresolvedBreedDependencyError]({
      BlueprintReader.read(res("blueprint/blueprint35.yml"))
    }) should have(
      'breed(DefaultBreed("solid-barbershop", Deployable("solid/barbershop"), Nil, Nil, Nil, Map("es" -> BreedReference("elastic-search")))),
      'dependency("es" -> BreedReference("elastic-search"))
    )
  }

  it should "expand the service with breed only object" in {
    BlueprintReader.read(res("blueprint/blueprint36.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("donut"), Nil, Nil, Nil, Map()), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the service with breed reference only object" in {
    BlueprintReader.read(res("blueprint/blueprint37.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - expanded" in {
    BlueprintReader.read(res("blueprint/blueprint38.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - service single element." in {
    BlueprintReader.read(res("blueprint/blueprint39.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - no service just cluster." in {
    BlueprintReader.read(res("blueprint/blueprint40.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - no service and compact breed." in {
    BlueprintReader.read(res("blueprint/blueprint41.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Some(ScaleReference("large")), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - cluster contains list." in {
    BlueprintReader.read(res("blueprint/blueprint42.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Some(ScaleReference("large")), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "fail on direct recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BlueprintReader.read(res("blueprint/blueprint43.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), Nil, Nil, Nil, Map("db" -> BreedReference("monarch"))))
    )
  }

  it should "fail on indirect recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BlueprintReader.read(res("blueprint/blueprint44.yml"))
    }) should have(
      'breed(DefaultBreed("monarch2", Deployable("magneticio/monarch2:latest"), Nil, Nil, Nil, Map("db" -> BreedReference("monarch1"))))
    )
  }

  it should "expand single reference filter to a list." in {
    BlueprintReader.read(res("blueprint/blueprint45.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), None, Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "expand single filter to a list." in {
    BlueprintReader.read(res("blueprint/blueprint46.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Some(ScaleReference("large")), Some(DefaultRouting("", None, List(DefaultFilter("", "user.agent == android")))))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }
}
