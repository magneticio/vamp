package io.magnetic.vamp_core.model.reader

import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.notification._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BlueprintReaderTest extends FlatSpec with Matchers with ReaderTest {

  "BlueprintReader" should "read the simplest YAML (name and single breed only)" in {
    BlueprintReader.read(res("blueprint1.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the endpoints and parameters" in {
    BlueprintReader.read(res("blueprint2.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("notorious.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map(Trait.Name.asName("notorious.ports.aspect") -> "thorium"))
    )
  }

  it should "read the reference sla" in {
    BlueprintReader.read(res("blueprint3.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(SlaReference("strong-mountain", List()))))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the reference sla with explicit name" in {
    BlueprintReader.read(res("blueprint4.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(SlaReference("strong-mountain", List()))))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the reference sla with escalations" in {
    BlueprintReader.read(res("blueprint5.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(SlaReference("strong-mountain", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious")))))))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the anonymous sla" in {
    BlueprintReader.read(res("blueprint6.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(GenericSla("", "vital-cloud", List(), Map("reborn" -> "red-swallow")))))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the anonymous sla with escalations" in {
    BlueprintReader.read(res("blueprint7.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), Some(GenericSla("", "vital-cloud", List(EscalationReference("red-flag")), Map("reborn" -> "red-swallow")))))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the anonymous scale" in {
    BlueprintReader.read(res("blueprint8.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Some(DefaultScale("", 0.2, 120, 2)), None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the reference scale" in {
    BlueprintReader.read(res("blueprint9.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Some(ScaleReference("large")), None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the reference scale with explicit name parameter" in {
    BlueprintReader.read(res("blueprint10.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Some(ScaleReference("large")), None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the reference routing" in {
    BlueprintReader.read(res("blueprint11.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(RoutingReference("conservative")))), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the routing with weight" in {
    BlueprintReader.read(res("blueprint12.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", Some(50), List())))), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the routing with filter reference" in {
    BlueprintReader.read(res("blueprint13.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the routing with filter references" in {
    BlueprintReader.read(res("blueprint14.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", None, List(FilterReference("android"), FilterReference("ios")))))), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read the routing with anonymous filter" in {
    BlueprintReader.read(res("blueprint15.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", Some(10), List(DefaultFilter("", "user.agent != ios")))))), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "fail on both reference and inline routing declarations" in {
    expectedError[EitherReferenceOrAnonymous]({
      BlueprintReader.read(res("blueprint16.yml"))
    }) should have(
      'name("routing"),
      'reference("!ios")
    )
  }

  it should "expand the filter list" in {
    BlueprintReader.read(res("blueprint17.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "expand the breed" in {
    BlueprintReader.read(res("blueprint18.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "expand the services" in {
    BlueprintReader.read(res("blueprint19.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "expand the cluster" in {
    BlueprintReader.read(res("blueprint20.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "expand the more complex blueprint" in {
    BlueprintReader.read(res("blueprint21.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(DefaultBreed("nocturnal-viper", Deployable("anaconda"), List(), List(), Map()), None, None)), Some(SlaReference("strong-mountain", List()))), Cluster("omega", List(Service(BreedReference("scary-lion"), None, None)), None), Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Some(DefaultScale("", 0.2, 120.0, 2)), Some(DefaultRouting("", Some(95), List(DefaultFilter("", "ua = android"))))), Service(BreedReference("remote-venus"), Some(ScaleReference("worthy")), None)), Some(GenericSla("", "vital-cloud", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious"))), Map("reborn" -> "red-swallow")))), Cluster("needless", List(Service(DefaultBreed("hideous-canal", Deployable("old/crystal"), List(), List(), Map()), None, None)), Some(SlaReference("fish-steamy", List()))))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map(Trait.Name.asName("omega.ports.aspect") -> "thorium"))
    )
  }

  it should "validate endpoints for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint22.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), List(HttpPort("port", None, Some(80), Trait.Direction.Out)), List(), Map()), None, None)), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }

  it should "validate endpoints for inline breeds - no cluster" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint23.yml"))
    }) should have(
      'name(Trait.Name.asName("omega.ports.port")),
      'value(Some(8080))
    )
  }

  it should "validate endpoints for inline breeds - not a port" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint24.yml"))
    }) should have(
      'name(Trait.Name.asName("supersonic.environment_variables.port")),
      'value(Some(8080))
    )
  }

  it should "validate endpoints for inline breeds - no port" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint25.yml"))
    }) should have(
      'name(Trait.Name.asName("supersonic.ports.http")),
      'value(Some(8080))
    )
  }

  it should "validate parameters for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint26.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), List(TcpPort("port", None, None, Trait.Direction.In)), List(), Map()), None, None)), None))),
      'endpoints(List()),
      'parameters(Map(Trait.Name.asName("supersonic.ports.port") -> "8080"))
    )
  }

  it should "validate parameters for inline breeds - no cluster" in {
    expectedError[UnresolvedParameterError]({
      BlueprintReader.read(res("blueprint27.yml"))
    }) should have(
      'name(Trait.Name.asName("omega.ports.port")),
      'value("8080")
    )
  }

  it should "validate parameters for inline breeds - not a trait" in {
    expectedError[UnresolvedParameterError]({
      BlueprintReader.read(res("blueprint28.yml"))
    }) should have(
      'name(Trait.Name.asName("supersonic.port")),
      'value("8080")
    )
  }

  it should "validate parameters for inline breeds - no trait" in {
    expectedError[UnresolvedParameterError]({
      BlueprintReader.read(res("blueprint29.yml"))
    }) should have(
      'name(Trait.Name.asName("supersonic.ports.http")),
      'value("8080")
    )
  }

  it should "validate parameters for inline breeds - no IN trait" in {
    expectedError[UnresolvedParameterError]({
      BlueprintReader.read(res("blueprint30.yml"))
    }) should have(
      'name(Trait.Name.asName("supersonic.ports.port")),
      'value("8080")
    )
  }

  it should "validate breed uniqueness across clusters" in {
    expectedError[NonUniqueBlueprintBreedReferenceError]({
      BlueprintReader.read(res("blueprint31.yml"))
    }) should have(
      'name("solid-barbershop")
    )
  }

  it should "validate breed uniqueness across services" in {
    expectedError[NonUniqueBlueprintBreedReferenceError]({
      BlueprintReader.read(res("blueprint32.yml"))
    }) should have(
      'name("solid-barbershop")
    )
  }

  it should "validate breed cross dependencies - no inline" in {
    BlueprintReader.read(res("blueprint33.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), None, None)), None), Cluster("notorious", List(Service(BreedReference("elastic-search"), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "validate breed cross dependencies - inline and valid" in {
    BlueprintReader.read(res("blueprint34.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("elastic-search"), None, None)), None), Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("solid/barbershop"), List(), List(), Map("es" -> BreedReference("elastic-search"))), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "validate breed cross dependencies - missing reference for an inline breed" in {
    expectedError[UnresolvedBreedDependencyError]({
      BlueprintReader.read(res("blueprint35.yml"))
    }) should have(
      'breed(DefaultBreed("solid-barbershop", Deployable("solid/barbershop"), List(), List(), Map("es" -> BreedReference("elastic-search")))),
      'dependency("es" -> BreedReference("elastic-search"))
    )
  }

  it should "expand the service with breed only object" in {
    BlueprintReader.read(res("blueprint36.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("donut"), List(), List(), Map()), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "expand the service with breed reference only object" in {
    BlueprintReader.read(res("blueprint37.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), None, None)), None))),
      'endpoints(List()),
      'parameters(Map())
    )
  }

  it should "read scale and routing - expanded" in {
    BlueprintReader.read(res("blueprint38.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(HttpPort("port", None, Some(80), Trait.Direction.Out)), List(), Map()), Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }

  it should "read scale and routing - service single element." in {
    BlueprintReader.read(res("blueprint39.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(HttpPort("port", None, Some(80), Trait.Direction.Out)), List(), Map()), Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }

  it should "read scale and routing - no service just cluster." in {
    BlueprintReader.read(res("blueprint40.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(HttpPort("port", None, Some(80), Trait.Direction.Out)), List(), Map()), Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }

  it should "read scale and routing - no service and compact breed." in {
    BlueprintReader.read(res("blueprint41.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Some(ScaleReference("large")), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }

  it should "read scale and routing - cluster contains list." in {
    BlueprintReader.read(res("blueprint42.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Some(ScaleReference("large")), Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }

  it should "fail on direct recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BlueprintReader.read(res("blueprint43.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(), Map("db" -> BreedReference("monarch"))))
    )
  }

  it should "fail on indirect recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BlueprintReader.read(res("blueprint44.yml"))
    }) should have(
      'breed(DefaultBreed("monarch2", Deployable("magneticio/monarch2:latest"), List(), List(), Map("db" -> BreedReference("monarch1"))))
    )
  }

  it should "expand single reference filter to a list." in {
    BlueprintReader.read(res("blueprint45.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), None, Some(DefaultRouting("", None, List(FilterReference("android")))))), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }

  it should "expand single filter to a list." in {
    BlueprintReader.read(res("blueprint46.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Some(ScaleReference("large")), Some(DefaultRouting("", None, List(DefaultFilter("", "user.agent == android")))))), None))),
      'endpoints(List(TcpPort(Trait.Name.asName("supersonic.ports.port"), None, Some(8080), Trait.Direction.Out))),
      'parameters(Map())
    )
  }
}
