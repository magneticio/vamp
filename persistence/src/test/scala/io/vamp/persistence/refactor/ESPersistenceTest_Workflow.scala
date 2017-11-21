package io.vamp.persistence.refactor

import java.time.ZoneOffset

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.vamp.common.{Id, Namespace, RootAnyMap}
import io.vamp.model.artifact._
import io.vamp.model.reader.{MegaByte, Quantity, Time}
import io.vamp.persistence.refactor.serialization.{SerializationSpecifier, VampJsonFormats}
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}

/**
 * Created by mihai on 11/10/17.
 */
class ESPersistenceTest_Workflow extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  behavior of "EsDao"

  it should "Correctly Persist Workflow objects" in { implicit namespace: Namespace ⇒
    val workflow1 = Workflow(
      name = "workflow_1", RootAnyMap(Map()),
      breed = DefaultBreed(name = "breed_01", RootAnyMap(Map()), deployable = Deployable(Some("deployableType"), "myDeployable"),
          ports = List[Port](
              Port(name = "p01", alias = Some("Alias001"), value = Some("val001"), number = 1, `type` = Port.Type.Tcp),
              Port(name = "p02", alias = Some("Alias002"), value = Some("val002"), number = 2, `type` = Port.Type.Http)
          ),
          environmentVariables = List[EnvironmentVariable](
              EnvironmentVariable(name = "env_var001", alias = Some("envVarAlias01"), value = Some("envVarValue01"), interpolated = Some("interpolated001")),
              EnvironmentVariable(name = "env_var002", alias = Some("envVarAlias02"), value = Some("envVarValue02"), interpolated = Some("interpolated002"))
          ),
          constants = List[Constant](
            Constant(name = "constant001", alias = Some("constant_alias01"), value = Some("constant_value001")),
            Constant(name = "constant002", alias = Some("constant_alias02"), value = Some("constant_value002"))
          ),
          arguments = List[Argument](
            Argument(key = "k01", value = "v01"),
            Argument(key = "k02", value = "v02")

          ),
          dependencies = Map[String, Breed](
            "dep01" -> BreedReference("dummyBreedReference")
          ),
          healthChecks = Some(List[HealthCheck](
            HealthCheck(path = "path001", port = "port001", initialDelay = Time(7), timeout = Time(8), interval = Time(9), failures = 10, protocol = "someProtocol"),
            HealthCheck(path = "path002", port = "port002", initialDelay = Time(1), timeout = Time(2), interval = Time(3), failures = 4, protocol = "someProtocol2")
          ))), status = Workflow.Status.Stopping,
      schedule = TimeSchedule(period = TimeSchedule.RepeatPeriod(days = Some(java.time.Period.of(2,3,1)), time = Some(java.time.Duration.ofSeconds(100,1101))),
        repeat = TimeSchedule.RepeatCount(7), start = Some(java.time.OffsetDateTime.of(1, 2, 3, 4, 5, 6, 7, ZoneOffset.of("+123456")))),
      scale = Some(DefaultScale(name = "name_007", metadata = RootAnyMap.empty, cpu = Quantity(3.14159), memory = MegaByte(143), instances = 5)),
        environmentVariables = List(EnvironmentVariable(name = "SimpleEnvVar", alias = Some("envVarAlias"),
          value = Some("evVarValue"), interpolated = Some("simpleInterpolatedValue")),
          EnvironmentVariable(name = "SimpleEnvVar2", alias = Some("envVarAlias3"),
            value = Some("evVarValue4"), interpolated = Some("simpleInterpolatedValue5"))
        ),
      arguments = List[Argument](Argument(key = "k001", value = "v001"), Argument(key = "k002", value = "v002")), network = Some("network007"),
      healthChecks = Some(List[HealthCheck](
        HealthCheck(path = "path011", port = "port011", initialDelay = Time(71), timeout = Time(81), interval = Time(91), failures = 101, protocol = "someProtocol1"),
        HealthCheck(path = "path022", port = "port022", initialDelay = Time(11), timeout = Time(21), interval = Time(31), failures = 41, protocol = "someProtocol21")
      )),
      dialects = RootAnyMap.empty,
      instances = List(Instance(name = "instance001", host = "host001", ports = Map("004" -> 4, "007" -> 7), deployed = true)),
      health = Some(Health(staged = 4, running = 3, healthy = 2, unhealthy = 7)
    ))

    // Create and retrieve; See that the object is there
    val workflow1Id = simpleAwait(VampPersistence().create(workflow1))
    assert(simpleAwait(VampPersistence().read(workflow1Id)) == workflow1)
  }

  it should "correctly persist Eorkflow Status objects" in { implicit ns: Namespace =>
    case class WorkflowStatusWrapper(id: String, workflowStatus: Workflow.Status)
    val statusWrapperDecoder: Decoder[WorkflowStatusWrapper] = deriveDecoder[WorkflowStatusWrapper]
    val statusWrapperEncoder: Encoder[WorkflowStatusWrapper] = deriveEncoder[WorkflowStatusWrapper]
    implicit val statusWrapperSerilizationSpecifier = SerializationSpecifier[WorkflowStatusWrapper](encoder = statusWrapperEncoder,
      decoder = statusWrapperDecoder, typeName = "statusWrapper", idExtractor = (e ⇒ Id[WorkflowStatusWrapper](e.id)))


    val obj1 = WorkflowStatusWrapper("1", Workflow.Status.Starting)
    val obj2 = WorkflowStatusWrapper("2", Workflow.Status.Stopping)
    val obj3 = WorkflowStatusWrapper("3", Workflow.Status.Running)
    val obj4 = WorkflowStatusWrapper("4", Workflow.Status.Suspended)
    val obj5 = WorkflowStatusWrapper("5", Workflow.Status.Stopping)
    val obj6 = WorkflowStatusWrapper("6", Workflow.Status.Restarting(Some(Workflow.Status.RestartingPhase.Starting)))
    val obj7 = WorkflowStatusWrapper("7", Workflow.Status.Restarting(Some(Workflow.Status.RestartingPhase.Stopping)))
    val obj8 = WorkflowStatusWrapper("8", Workflow.Status.Restarting(None))

    simpleAwait(VampPersistence().create(obj1))
    simpleAwait(VampPersistence().create(obj2))
    simpleAwait(VampPersistence().create(obj3))
    simpleAwait(VampPersistence().create(obj4))
    simpleAwait(VampPersistence().create(obj5))
    simpleAwait(VampPersistence().create(obj6))
    simpleAwait(VampPersistence().create(obj7))
    simpleAwait(VampPersistence().create(obj8))

    assert(obj1 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj1))))
    assert(obj2 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj2))))
    assert(obj3 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj3))))
    assert(obj4 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj4))))
    assert(obj5 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj5))))
    assert(obj6 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj6))))
    assert(obj7 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj7))))
    assert(obj8 == simpleAwait(VampPersistence().read(statusWrapperSerilizationSpecifier.idExtractor(obj8))))


  }


}
