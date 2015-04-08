package io.vamp.core.persistence

import java.util.concurrent.TimeUnit

import io.vamp.core.model.artifact.DeploymentService.Deployed
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.UnsupportedPersistenceRequest

import scala.concurrent.duration.FiniteDuration

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

  val filter1 = DefaultFilter(name = "filter1", condition = "my-condition-1")
  val filter1Updated = filter1.copy(condition = "updated condition")
  val filter2 = DefaultFilter(name = "filter2", condition = "my-condition-2")

  val filterAnonymous = DefaultFilter(name = "", condition = "test-anonymous")
  val filterRef1 = FilterReference(name = "referenced_filter")

  val route4 = DefaultRouting(name = "route4", weight = Some(1), filters = List(filter1.copy(condition = "route4-condition"), filterAnonymous, filterRef1))
  val route4Updated = DefaultRouting(name = "route4", weight = Some(12), filters = List(filter1Updated))
  val route5 = DefaultRouting(name = "route5", weight = None, filters = List(filter2))

  val sla7 = EscalationOnlySla(name = "sla7", escalations = List.empty)
  val sla7Updated = sla7.copy(escalations = List(GenericEscalation(name = "sla7-escalation", `type` = "my-type7", parameters = Map.empty)))
  val sla8 = ResponseTimeSlidingWindowSla(name = "sla8",
    upper = FiniteDuration(length = 1, unit = TimeUnit.HOURS),
    lower = FiniteDuration(length = 10, unit = TimeUnit.MINUTES),
    interval = FiniteDuration(length = 5, unit = TimeUnit.SECONDS),
    cooldown = FiniteDuration(length = 15, unit = TimeUnit.MILLISECONDS),
    escalations = List.empty)

  val escalation4 = GenericEscalation(name = "escalation4", `type` = "my-type", parameters = Map("my-first" -> "This is a string value"))
  val escalation4Updated = escalation4.copy(`type` = "my-other-type", parameters = Map.empty)
  val escalation5 = GenericEscalation(name = "escalation5", `type` = "my-type", parameters = Map("my-first" -> 1))

  val sla4 = GenericSla(name = "sla4", `type` = "aType", escalations = List(escalation4.copy(name = "sla4-escalation1"), escalation5.copy(name = "sla4-escalation2")), parameters = Map("my-first" -> "This is a another string value"))
  val sla4Updated = sla4.copy(`type` = "aType-updated", escalations = List.empty, parameters = Map.empty)
  val sla5 = GenericSla(name = "sla5", `type` = "aType", escalations = List(EscalationReference(name = "for-reference-only")), parameters = Map("my-first" -> 1))

  val escalation7 = ScaleInstancesEscalation(name = "escalation-7", minimum = 1, maximum = 5, scaleBy = 2, targetCluster = Some("target-cluster-1"))
  val escalation7Updated = ScaleCpuEscalation(name = "escalation-7", minimum = 1.2, maximum = 6, scaleBy = 0.4, targetCluster = Some("target-cluster-2"))
  val escalation8 = ScaleMemoryEscalation(name = "escalation-8", minimum = 64, maximum = 4096, scaleBy = 32, targetCluster = Some("target-cluster-1"))

  val escalation11 = ToOneEscalation(name = "escalation11", escalations = List(escalation7.copy(name = ""), escalation8.copy(name = "")))
  val escalation11Updated = escalation11.copy(escalations = List(escalation7.copy(name = "")))
  val escalation12 = ToAllEscalation(name = "escalation12", escalations = List(escalation7.copy(name = ""), escalation8.copy(name = "")))

  private val minimalBreedReference = BreedReference("minimal-breed")
  private val myRoute = DefaultRouting(name = "my-route", weight = Some(1), filters = List(DefaultFilter(name = "my-filter", condition = "my-condition")))
  private val myService1 = Service(breed = minimalBreedReference, scale = Some(myScale1), routing = None)
  private val myService2 = Service(breed = minimalBreedReference, scale = Some(myScale2), routing = Some(myRoute))
  private val myEscalation = GenericEscalation(name = "my-escalation", `type` = "my-type", parameters = Map("param1" -> 1, "param2" -> "Hello"))
  private val mySlaReference = SlaReference(name = "my-sla", escalations = List(myEscalation))
  private val mySlidingWindowSla = ResponseTimeSlidingWindowSla("",
    lower = FiniteDuration(100, TimeUnit.MILLISECONDS),
    upper = FiniteDuration(100, TimeUnit.MILLISECONDS),
    interval = FiniteDuration(5, TimeUnit.MINUTES),
    cooldown = FiniteDuration(10, TimeUnit.MINUTES),
    escalations = List(GenericEscalation(name = "", `type` = "my-sliding-escalation", parameters = Map("param1" -> 1, "param2" -> "Hello"))))
  private val myCluster_app = Cluster(name = "app", services = List(myService1), sla = Some(mySlidingWindowSla))
  private val myCluster_logger = Cluster(name = "logger", services = List(myService1), sla = None)
  private val myEndpointPort1 = HttpPort(name = "port8080", alias = Option("HTTP"), value = Option(8080), direction = Trait.Direction.In)
  private val myEndpointPort2 = TcpPort(name = "port21", alias = Option("FTP"), value = Option(8080), direction = Trait.Direction.In)
  private val myParameter1 = (Trait.Name(Some("myParameter1"), None, "GO_HOME"), "/var/lib/go/bin")
  private val myParameter2 = (Trait.Name(Some("myParameter2"), Some(Trait.Name.Group.EnvironmentVariables), "PATH"), "/opt/java/bin")
  private val myParameter3 = (Trait.Name(Some("myParameter3"), Some(Trait.Name.Group.Ports), "HOME_PORT"), 23)

  private val myEndpointPort5 = HttpPort(name = "port8080", alias = Option("HTTP"), value = Option(8080), direction = Trait.Direction.In)
  private val myEndpointPort6 = TcpPort(name = "port21", alias = Option("FTP"), value = Option(8080), direction = Trait.Direction.In)
  private val myParameter5 = (Trait.Name(Some("myParameter1"), None, "GO_HOME"), "/var/lib/go/bin")
  private val myParameter6 = (Trait.Name(Some("myParameter2"), Some(Trait.Name.Group.EnvironmentVariables), "PATH"), "/opt/java/bin")
  private val myParameter7 = (Trait.Name(Some("myParameter3"), Some(Trait.Name.Group.Ports), "HOME_PORT"), 23)


  val myCluster_db = Cluster(name = "db", services = List(myService2), sla = None)
  private val blueprintMinimal = DefaultBlueprint(
    name = "blueprint_minimal",
    clusters = List.empty,
    endpoints = List.empty,
    environmentVariables = Map.empty
  )
  val blueprintMinimalUpdatedWithCluster = TestData.blueprintMinimal.copy(clusters = List(myCluster_logger))

  val blueprintWithFullSla = DefaultBlueprint(
    name = "blueprint_with_full_sla",
    clusters = List(
      myCluster_db.copy(
        name = "cluster-with-sla",
        sla = Some(mySlaReference.copy(name = "cluster-sla1")))),
    endpoints = List.empty,
    environmentVariables = Map.empty)
  val blueprintWithFullSlaUpdated = blueprintWithFullSla.copy(clusters = List.empty)
  val blueprintWithFullService = DefaultBlueprint(
    name = "blueprint_with_full_service",
    clusters = List(
      myCluster_db.copy(
        name = "cluster-without-sla",
        sla = None,
        services = List(Service(breed = breed1Updated.copy(name = "full-service-breed"), routing = Some(route4), scale = Some(myScale2)))
      ),
      myCluster_db.copy(
        name = "cluster-without-sla-2",
        sla = None,
        services = List(Service(breed = breed1Updated.copy(name = "full-service-breed2"), routing = Some(route4), scale = Some(myScale2)))
      )
    ),
    endpoints = List.empty,
    environmentVariables = Map.empty)
  val blueprintWithFullServiceUpdated = blueprintWithFullService.copy(clusters = List.empty)
  val blueprintFull = DefaultBlueprint(
    name = "blueprint_full",
    clusters = List(myCluster_db, myCluster_app),
    endpoints = List(myEndpointPort1, myEndpointPort2),
    environmentVariables = Map(myParameter1, myParameter2, myParameter3)
  )


  val deploymentServer1 = DeploymentServer(name = "deployment-1-server-1", host = "vamp.magnetic.io", ports = Map(80 -> 8080, 22 -> 2222), deployed = true)

  val deploymentServiceBreed1 = DefaultBreed(
    name = "wp4",
    deployable = Deployable("Wordpress 4.0"),
    ports = List(
      HttpPort(name = "port8080", alias = Option("HTTP"), value = Option(8080), direction = Trait.Direction.In)
    ),
    environmentVariables = List(
      EnvironmentVariable(name = "UPPER_MEM", alias = None, value = Some("128K"), direction = Trait.Direction.Out)
    ),
    dependencies = Map("db" -> BreedReference(name = "mysql"))
  )

  val deploymentService1 = DeploymentService(
    state = Deployed(),
    servers = List(deploymentServer1),
    breed = deploymentServiceBreed1,
    scale = Some(DefaultScale(name = "my-scale2", cpu = 2, memory = 4096, instances = 4)),
    routing = Some(DefaultRouting(name = "route5", weight = None, filters = List.empty)),
    dependencies = Map("abc" -> "def")
  )


  val deploymentService2 = deploymentService1.copy(breed = deploymentServiceBreed1.copy(name="another_version"))

  val deployment1 = Deployment(
    name = "deployment-1",
    clusters = List.empty,
    endpoints = List.empty,
    environmentVariables = Map(myParameter5, myParameter6)
  )

  val deployment1Updated = deployment1.copy(
    clusters = List(
      DeploymentCluster(
        name = "deployment-cluster-1",
        services = List(deploymentService1.copy(state = DeploymentService.ReadyForUndeployment()), deploymentService2),
        sla = Some(SlaReference("sla-ref-deployment1", escalations = List.empty)),
        routes = Map(80 -> 23890, 8080 -> 45720)
      )
    ),
    endpoints = List(myEndpointPort5, myEndpointPort6),
    environmentVariables = Map(myParameter5, myParameter6, myParameter7)
  )

  val deployment2 = Deployment(
    name = "deployment-2",
    clusters = List(
      DeploymentCluster(
        name = "deployment-cluster-2",
        services = List.empty,
        sla = Some(SlaReference("sla-ref-deployment2", escalations = List.empty)),
        routes = Map.empty
      )
    ),
    endpoints = List.empty,
    environmentVariables = Map.empty
  )

  val deploymentServiceWithError = deploymentService1.copy(state = DeploymentService.Error(UnsupportedPersistenceRequest("ERROR")))
  val deployment4WithErrorService = Deployment(
    name = "deployment-4",
    clusters = List(
      DeploymentCluster(
        name = "deployment-cluster-2",
        services = List(deploymentServiceWithError),
        sla = None,
        routes = Map.empty
      )
    ),
    endpoints = List.empty,
    environmentVariables = Map.empty
  )

  val deployment5Deployed = Deployment(
    name = "deployment-5",
    clusters = List(
      DeploymentCluster(
        name = "deployment-cluster-2",
        services = List(deploymentService1),
        sla = None,
        routes = Map.empty
      )
    ),
    endpoints = List.empty,
    environmentVariables = Map.empty
  )


}
