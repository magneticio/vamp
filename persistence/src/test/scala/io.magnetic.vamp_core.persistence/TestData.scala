package io.magnetic.vamp_core.persistence

import io.magnetic.vamp_core.model.artifact._

/**
 * Testdata
 */
object TestData {

  val envVar1 = EnvironmentVariable(name = "JAVA_HOME", alias = Some("JAVAHOME"), value = Some("/opt/java/bin"), direction = Trait.Direction.In)
  val envVar1Updated = EnvironmentVariable(name = "JAVA_HOME", alias = Some("JAVA_HOME"), value = Some("/usr/lib/java/bin"), direction = Trait.Direction.Out)
  val envVar2 = EnvironmentVariable(name = "HI_MEM", alias = None, value = Some("64K"), direction = Trait.Direction.Out)

  val breedSimple = DefaultBreed(name = "mysql-backup", deployable = Deployable("backup"), environmentVariables = List.empty, ports = List.empty, dependencies = Map.empty)

  val breed1 = DefaultBreed(
    name = "wp4",
    deployable = Deployable("Wordpress 4.0"),
    ports = List(
      HttpPort(name = "port8080", alias = Option("HTTP"), value = Option(8080), direction = Trait.Direction.In)
    ),
    environmentVariables = List(
      EnvironmentVariable(name = "HI_MEM", alias = None, value = Some("64K"), direction = Trait.Direction.Out)
    ),
    dependencies = Map("db" -> BreedReference(name = "mysql"))
  )

  val breedAnonymous = breed1.copy(name = "")

  val breed1Updated = breed1.copy(
    deployable = Deployable("Wordpress 5.0"),
    environmentVariables = List(envVar1Updated),
    ports = List(TcpPort(name = "port23", alias = Option("Telnet"), value = Option(23), direction = Trait.Direction.In)),
    dependencies = Map(
      "db" -> BreedReference(name = "postgres"),
      "http" -> BreedReference(name = "nginx"),
      //"backup" -> breedSimple,
      "recovery" -> breedAnonymous,
      "recovery2" -> breedAnonymous.copy(ports = List.empty, dependencies = Map.empty)
    )
  )

  val breed2 = DefaultBreed(
    name = "wp 4.1",
    deployable = Deployable("Wordpress 4.1"),
    ports = List(
      HttpPort(name = "port80", alias = Option("HTTP"), value = Option(80), direction = Trait.Direction.In),
      TcpPort(name = "port22", alias = Option("SSH"), value = Option(22), direction = Trait.Direction.Out)
    ),
    environmentVariables = List(
      EnvironmentVariable(name = "JAVA_HOME", alias = Some("JAVAHOME"), value = Some("/opt/java/bin"), direction = Trait.Direction.In)
    ),
    dependencies = Map("db" -> BreedReference(name = "mysql"))
  )

  val myScale1 = DefaultScale(name = "my-scale", cpu = 0.4, memory = 512, instances = 1)
  val myScale1Updated = myScale1.copy(name = "my-scale", cpu = 2, memory = 128, instances = 5)
  val myScale2 = DefaultScale(name = "my-scale2", cpu = 2, memory = 4096, instances = 4)

  val filter1 = DefaultFilter(name = "filter1", condition = "my-condition")
  val filter1Updated = filter1.copy(condition = "updated condition")
  val filter2 = DefaultFilter(name = "filter2", condition = "my-condition")

  val filterAnonymous = DefaultFilter(name = "", condition = "test")
  val filterRef1 = FilterReference(name = "referenced_filter")

  val routeSimple1 = DefaultRouting(name = "simpleRoute1", weight = Some(1), filters = List.empty)
  val routeSimple1Updated = routeSimple1.copy(weight = Some(12), filters = List.empty)
  val routeSimple2 = DefaultRouting(name = "simpleRoute2", weight = None, filters = List.empty)

  val route4 = DefaultRouting(name = "route4", weight = Some(1), filters = List(filter1, filterAnonymous, filterRef1))
  val route4Updated = DefaultRouting(name = "route4", weight = Some(12), filters = List(filter1Updated))
  val route5 = DefaultRouting(name = "route5", weight = None, filters = List(filter2))

  val sla1 = DefaultSla(name = "sla1", `type` = "aType", escalations = List.empty, parameters = Map.empty)
  val sla1Updated = sla1.copy(`type` = "aType-updated", escalations = List.empty, parameters = Map.empty)
  val sla2 = DefaultSla(name = "sla2", `type` = "aType", escalations = List.empty, parameters = Map.empty)

  val escalation1 = DefaultEscalation(name = "escalation1", `type` = "my-type", parameters = Map.empty)
  val escalation1Updated = escalation1.copy(`type` = "my-other-type", parameters = Map.empty)
  val escalation2 = DefaultEscalation(name = "escalation2", `type` = "my-type", parameters = Map.empty)

  val escalation4 = DefaultEscalation(name = "escalation4", `type` = "my-type", parameters = Map("my-first" -> "This is a string value"))
  val escalation4Updated = escalation4.copy(`type` = "my-other-type", parameters = Map.empty)
  val escalation5 = DefaultEscalation(name = "escalation5", `type` = "my-type", parameters = Map("my-first" -> 1))

  private val minimalBreedReference = BreedReference("minimal-breed")
  private val myRoute = DefaultRouting(name = "my-route", weight = Some(1), filters = List(DefaultFilter(name = "my-filter", condition = "my-condition")))
  private val myService1 = Service(breed = minimalBreedReference, scale = Some(myScale1), routing = None)
  private val myService2 = Service(breed = minimalBreedReference, scale = Some(myScale2), routing = Some(myRoute))
  private val myEscalation = DefaultEscalation(name = "my-escalation", `type` = "my-type", parameters = Map("param1" -> 1, "param2" -> "Hello"))
  private val mySla = SlaReference(name = "my-sla", escalations = List(myEscalation))
  private val myCluster_app = Cluster(name = "app", services = List(myService1), sla = Some(mySla))
  private val myCluster_logger = Cluster(name = "logger", services = List(myService1), sla = None)
  private val myEndpointPort1 = HttpPort(name = "port8080", alias = Option("HTTP"), value = Option(8080), direction = Trait.Direction.In)
  private val myEndpointPort2 = TcpPort(name = "port21", alias = Option("FTP"), value = Option(8080), direction = Trait.Direction.In)
  private val myParameter1 = (Trait.Name(Some("myParameter1"), None, "GO_HOME"), "/var/lib/go/bin")
  private val myParameter2 = (Trait.Name(Some("myParameter2"), Some(Trait.Name.Group.EnvironmentVariables), "PATH"), EnvironmentVariable(name = "", alias = Some("JAVA_HOME"), value = Some("/opt/java/bin"), direction = Trait.Direction.In))
  private val myParameter3 = (Trait.Name(Some("myParameter3"), Some(Trait.Name.Group.Ports), "HOME_PORT"), TcpPort(name = "", alias = Option("Telnet"), value = Option(23), direction = Trait.Direction.In))


  val sla4 = DefaultSla(name = "sla4", `type` = "aType", escalations = List(escalation4.copy(name = "sla4-escalation1"), escalation5.copy(name = "sla4-escalation2")), parameters = Map("my-first" -> "This is a another string value"))
  val sla4Updated = sla4.copy(`type` = "aType-updated", escalations = List.empty, parameters = Map.empty)
  val sla5 = DefaultSla(name = "sla5", `type` = "aType", escalations = List(EscalationReference(name = "for-reference-only")), parameters = Map("my-first" -> 1))
  val myCluster_db = Cluster(name = "db", services = List(myService2), sla = None)
  val blueprintMinimal = DefaultBlueprint(
    name = "blueprint_minimal",
    clusters = List.empty,
    endpoints = List.empty,
    parameters = Map.empty
  )
  val blueprintMinimalUpdatedWithCluster = TestData.blueprintMinimal.copy(clusters = List(myCluster_logger))
  val blueprintMinimal2 = blueprintMinimalUpdatedWithCluster.copy(name = "blueprint-minimal2")
  val blueprintWithFullSla = DefaultBlueprint(
    name = "blueprint_with_full_sla",
    clusters = List(
      myCluster_db.copy(
        name = "cluster-with-sla",
        sla = Some(mySla.copy(name = "cluster-sla1")))),
    endpoints = List.empty,
    parameters = Map.empty)
  val blueprintWithFullSlaUpdated = blueprintWithFullSla.copy(clusters = List.empty)
  val blueprintWithFullService = DefaultBlueprint(
    name = "blueprint_with_full_service",
    clusters = List(
      myCluster_db.copy(
        name = "cluster-with-sla",
        sla = None,
        services = List(Service(breed = breed1Updated.copy(name = "full-service-breed"), routing = Some(route4), scale = Some(myScale2)))
      )),
    endpoints = List.empty,
    parameters = Map.empty)
  val blueprintWithFullServiceUpdated = blueprintWithFullService.copy(clusters = List.empty)
  val blueprintFull = DefaultBlueprint(
    name = "blueprint_full",
    clusters = List(myCluster_db, myCluster_app),
    endpoints = List(myEndpointPort1, myEndpointPort2),
    parameters = Map(myParameter1, myParameter2, myParameter3)
  )

}
