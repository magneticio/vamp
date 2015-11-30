package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BlueprintReaderTest extends FlatSpec with Matchers with ReaderTest {

  "BlueprintReader" should "read the simplest YAML (name and single breed only)" in {
    BlueprintReader.read(res("blueprint/blueprint1.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the endpoints and parameters" in {
    BlueprintReader.read(res("blueprint/blueprint2.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), None))),
      'endpoints(List(Port("notorious.ports.port", None, Some("8080")))),
      'environmentVariables(List(EnvironmentVariable("notorious.environment_variables.aspect", None, Some("thorium"))))
    )
  }

  it should "read the reference sla" in {
    BlueprintReader.read(res("blueprint/blueprint3.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), Some(SlaReference("strong-mountain", Nil))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference sla with explicit name" in {
    BlueprintReader.read(res("blueprint/blueprint4.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), Some(SlaReference("strong-mountain", Nil))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference sla with escalations" in {
    BlueprintReader.read(res("blueprint/blueprint5.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), Some(SlaReference("strong-mountain", List(ToAllEscalation("", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious")))))))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous sla" in {
    BlueprintReader.read(res("blueprint/blueprint6.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), Some(GenericSla("", "vital-cloud", Nil, Map("reborn" -> "red-swallow")))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous sla with escalations" in {
    BlueprintReader.read(res("blueprint/blueprint7.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), Some(GenericSla("", "vital-cloud", List(EscalationReference("red-flag")), Map("reborn" -> "red-swallow")))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous scale" in {
    BlueprintReader.read(res("blueprint/blueprint8.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, Some(DefaultScale("", 0.2, 120, 2)), None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference scale" in {
    BlueprintReader.read(res("blueprint/blueprint9.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, Some(ScaleReference("large")), None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference scale with explicit name parameter" in {
    BlueprintReader.read(res("blueprint/blueprint10.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, Some(ScaleReference("large")), None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference routing" in {
    BlueprintReader.read(res("blueprint/blueprint11.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(RoutingReference("conservative")))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with weight" in {
    BlueprintReader.read(res("blueprint/blueprint12.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(DefaultRouting("", Some(50), Nil, None)))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with filter reference" in {
    BlueprintReader.read(res("blueprint/blueprint13.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with filter references" in {
    BlueprintReader.read(res("blueprint/blueprint14.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(DefaultRouting("", None, List(FilterReference("android"), FilterReference("ios")), None)))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the routing with anonymous filter" in {
    BlueprintReader.read(res("blueprint/blueprint15.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(DefaultRouting("", Some(10), List(DefaultFilter("", "user.agent != ios")), None)))), None))),
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
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the breed" in {
    BlueprintReader.read(res("blueprint/blueprint18.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the services" in {
    BlueprintReader.read(res("blueprint/blueprint19.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the cluster" in {
    BlueprintReader.read(res("blueprint/blueprint20.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the more complex blueprint" in {
    BlueprintReader.read(res("blueprint/blueprint21.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Nil, Some(DefaultScale("", 0.2, 120.0, 2)), Some(DefaultRouting("", Some(95), List(DefaultFilter("", "ua = android")), None))), Service(BreedReference("remote-venus"), Nil, Some(ScaleReference("worthy")), None)), Some(GenericSla("", "vital-cloud", List(ToAllEscalation("", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious"))))), Map("reborn" -> "red-swallow")))), Cluster("notorious", List(Service(DefaultBreed("nocturnal-viper", Deployable("anaconda"), Nil, Nil, Nil, Map()), Nil, None, None)), None), Cluster("needless", List(Service(DefaultBreed("hideous-canal", Deployable("old/crystal"), Nil, Nil, Nil, Map()), Nil, None, None)), Some(SlaReference("fish-steamy", Nil))), Cluster("omega", List(Service(BreedReference("scary-lion"), Nil, None, None)), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(List(EnvironmentVariable("omega.environment_variables.aspect", None, Some("thorium"))))
    )
  }

  it should "validate endpoints for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint/blueprint22.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), Nil, None, None)), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "validate endpoints for inline breeds - no cluster" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint/blueprint23.yml"))
    }) should have(
      'name("omega.port"),
      'value(Some("8080"))
    )
  }

  it should "validate endpoints for inline breeds - not a port" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint/blueprint24.yml"))
    }) should have(
      'name("supersonic.port"),
      'value(Some("8080"))
    )
  }

  it should "validate endpoints for inline breeds - no port" in {
    expectedError[UnresolvedEndpointPortError]({
      BlueprintReader.read(res("blueprint/blueprint25.yml"))
    }) should have(
      'name("supersonic.http"),
      'value(Some("8080"))
    )
  }

  it should "validate environment variables for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint/blueprint26.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), Nil, List(EnvironmentVariable("port", None, None)), Nil, Map()), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(List(EnvironmentVariable("supersonic.environment_variables.port", None, Some("8080"))))
    )
  }

  it should "validate environment variables for inline breeds - no cluster" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint27.yml"))
    }) should have(
      'name("omega.port"),
      'value("8080")
    )
  }

  it should "validate environment variables for inline breeds - not a trait" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint28.yml"))
    }) should have(
      'name("supersonic.port"),
      'value("8080")
    )
  }

  it should "validate environment variables for inline breeds - no trait" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint29.yml"))
    }) should have(
      'name("supersonic.http"),
      'value("8080")
    )
  }

  it should "validate environment variables for setting port values" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint30.yml"))
    }) should have(
      'name("supersonic.port"),
      'value("8080")
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
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Nil, None, None)), None), Cluster("notorious", List(Service(BreedReference("elastic-search"), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "validate breed cross dependencies - inline and valid" in {
    BlueprintReader.read(res("blueprint/blueprint34.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("solid/barbershop"), Nil, Nil, Nil, Map("es" -> BreedReference("elastic-search"))), Nil, None, None)), None), Cluster("notorious", List(Service(BreedReference("elastic-search"), Nil, None, None)), None))),
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
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("donut"), Nil, Nil, Nil, Map()), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the service with breed reference only object" in {
    BlueprintReader.read(res("blueprint/blueprint37.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Nil, None, None)), None))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - expanded" in {
    BlueprintReader.read(res("blueprint/blueprint38.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), Nil, Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - service single element." in {
    BlueprintReader.read(res("blueprint/blueprint39.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), Nil, Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - no service just cluster." in {
    BlueprintReader.read(res("blueprint/blueprint40.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("wordpress1", Deployable("tutum/wordpress:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Map()), Nil, Some(DefaultScale("", 0.5, 512.0, 1)), Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - no service and compact breed." in {
    BlueprintReader.read(res("blueprint/blueprint41.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Nil, Some(ScaleReference("large")), Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and routing - cluster contains list." in {
    BlueprintReader.read(res("blueprint/blueprint42.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Nil, Some(ScaleReference("large")), Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
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

  it should "expand single reference filter to a list" in {
    BlueprintReader.read(res("blueprint/blueprint45.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Nil, None, Some(DefaultRouting("", None, List(FilterReference("android")), None)))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "expand single filter to a list" in {
    BlueprintReader.read(res("blueprint/blueprint46.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("wordpress1"), Nil, Some(ScaleReference("large")), Some(DefaultRouting("", None, List(DefaultFilter("", "user.agent == android")), None)))), None))),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")))),
      'environmentVariables(Nil)
    )
  }

  it should "parse dialects" in {
    BlueprintReader.read(res("blueprint/blueprint47.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, None, None, Map(Dialect.Marathon -> Map("a" -> "b"), Dialect.Docker -> Map("c" -> "d"))), Service(BreedReference("sava2"), Nil, None, None, Map())), None, Map(Dialect.Marathon -> Map("r" -> "t"), Dialect.Docker -> Map("q" -> "w", "o" -> "p"))), Cluster("viper", List(Service(BreedReference("sava3"), Nil, None, None, Map()), Service(BreedReference("sava4"), Nil, None, None, Map())), None, Map(Dialect.Marathon -> Map("u" -> "i"))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand and parse dialects" in {
    BlueprintReader.read(res("blueprint/blueprint48.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, None, None, Map(Dialect.Marathon -> Map("a" -> "b"), Dialect.Docker -> Map("c" -> "d"))), Service(BreedReference("sava2"), Nil, None, None, Map())), None, Map(Dialect.Marathon -> Map("r" -> "t"), Dialect.Docker -> Map("q" -> "w", "o" -> "p"))), Cluster("viper", List(Service(BreedReference("sava3"), Nil, None, None, Map()), Service(BreedReference("sava4"), Nil, None, None, Map())), None, Map(Dialect.Marathon -> Map("u" -> "i"))))),
      'endpoints(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "not allow blueprint with no service" in {
    expectedError[NoServiceError.type]({
      BlueprintReader.read(res("blueprint/blueprint49.yml"))
    })
  }

  it should "read service environment variables" in {
    BlueprintReader.read(res("blueprint/blueprint50.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("docker://vamp/solid-barbershop"), Nil, List(EnvironmentVariable("HEAP", None, Some("1024MB"))), Nil, Map()), List(EnvironmentVariable("HEAP", None, Some("2GB"), None)), None, None, Map())), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "read service environment variables for ref breed" in {
    BlueprintReader.read(res("blueprint/blueprint51.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), List(EnvironmentVariable("HEAP", None, Some("2GB"), None)), None, None, Map())), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "not allow service override of non existing breed environment variables" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BlueprintReader.read(res("blueprint/blueprint52.yml"))
    }) should have(
      'breed(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), Nil, Nil, Nil, Map())),
      'reference("HEAP")
    )
  }

  it should "not allow an empty service level environment variable" in {
    expectedError[MissingEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint53.yml"))
    }) should have(
      'breed(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), Nil, List(EnvironmentVariable("HEAP", None, Some("1024MB"))), Nil, Map())),
      'name("HEAP")
    )
  }

  it should "process default port type" in {
    val blueprint = BlueprintReader.read(res("blueprint/blueprint54.yml")).asInstanceOf[DefaultBlueprint]

    blueprint should have(
      'name("nomadic-frostbite"),
      'endpoints(List(Port("supersonic.ports.port", None, Some("8080")), Port("supersonic.ports.health", None, Some("8080/tcp")), Port("supersonic.ports.metrics", None, Some("8080/http")))),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("docker", Some("vamp/solid-barbershop")), List(Port("port", None, Some("80/http")), Port("health", None, Some("8080")), Port("metrics", None, Some("8090/tcp"))), Nil, Nil, Map()), Nil, None, None, Map())), None, Map())))
    )

    blueprint.endpoints.foreach {
      case port: Port if port.name == "supersonic.ports.port"    ⇒ port.`type` shouldBe Port.Http
      case port: Port if port.name == "supersonic.ports.health"  ⇒ port.`type` shouldBe Port.Tcp
      case port: Port if port.name == "supersonic.ports.metrics" ⇒ port.`type` shouldBe Port.Http
    }

    blueprint.clusters.find(_.name == "supersonic") foreach {
      case cluster ⇒ cluster.services.find(service ⇒ service.breed.name == "solid-barbershop") foreach { service ⇒
        service.breed.asInstanceOf[DefaultBreed].ports.foreach {
          case port: Port if port.name == "port"    ⇒ port.`type` shouldBe Port.Http
          case port: Port if port.name == "health"  ⇒ port.`type` shouldBe Port.Http
          case port: Port if port.name == "metrics" ⇒ port.`type` shouldBe Port.Tcp
        }
      }
    }
  }

  it should "report not supported dialect" in {
    expectedError[UnexpectedElement]({
      BlueprintReader.read(res("blueprint/blueprint55.yml"))
    }) should have(
      'element(Map("clusters" -> Map("supersonic" -> Map("services" -> List(Map("dialects" -> Map("google" -> Map("e" -> "f")))), "dialects" -> Map("google" -> Map("w" -> "e", "e" -> "r"))))))
    )
  }

  it should "report not supported element 'scala'" in {
    expectedError[UnexpectedElement]({
      BlueprintReader.read(res("blueprint/blueprint56.yml"))
    }) should have(
      'element(Map("clusters" -> Map("sava" -> Map("services" -> List(Map("scala" -> Map("cpu" -> 0.2, "memory" -> 128, "instances" -> 1)))))))
    )
  }

  it should "read sticky service" in {
    BlueprintReader.read(res("blueprint/blueprint57.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(DefaultRouting("", None, Nil, Some(Routing.Service))), Map())), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "read sticky server" in {
    BlueprintReader.read(res("blueprint/blueprint58.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Some(DefaultRouting("", None, Nil, Some(Routing.Server))), Map())), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "report illegal sticky value" in {
    expectedError[IllegalRoutingStickyValue]({
      BlueprintReader.read(res("blueprint/blueprint59.yml"))
    }) should have(
      'sticky("none")
    )
  }

  it should "report illegal cluster name" in {
    expectedError[IllegalName]({
      BlueprintReader.read(res("blueprint/blueprint60.yml"))
    }) should have(
      'name("notorious/snake")
    )
  }
}
