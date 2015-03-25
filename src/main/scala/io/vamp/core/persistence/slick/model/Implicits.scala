package io.vamp.core.persistence.slick.model

import java.time.OffsetDateTime

import io.vamp.core.model.artifact.DeploymentService._
import io.vamp.core.model.artifact.{Deployment, _}
import io.vamp.core.persistence.notification.PersistenceOperationFailure
import io.vamp.core.persistence.slick.model.DeploymentStateType.DeploymentStateType
import io.vamp.core.persistence.slick.model.EnvironmentVariableParentType.EnvironmentVariableParentType
import io.vamp.core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.vamp.core.persistence.slick.model.ParameterType.ParameterType
import io.vamp.core.persistence.slick.model.PortParentType.PortParentType
import io.vamp.core.persistence.slick.model.PortType.PortType
import io.vamp.core.persistence.slick.model.TraitParameterParentType.TraitParameterParentType
import io.vamp.core.persistence.slick.util.VampPersistenceUtil

import scala.language.implicitConversions
import scala.slick.driver.JdbcDriver.simple._

/**
 * Implicit conversions for Slick columns
 */
object Implicits {

  implicit val traitDirectionMapper = MappedColumnType.base[Trait.Direction.Value, String](
  { c => c.toString }, { s => Trait.Direction.withName(s) }
  )

  implicit val dependencyTypeMapper = MappedColumnType.base[DependencyType.Value, String](
  { c => c.toString }, { s => DependencyType.withName(s) }
  )

  val portTypeMap = Map(
    PortType.HTTP -> "http",
    PortType.TCP -> "tcp"
  )
  implicit val portTypeColumnTypeMapper = MappedColumnType.base[PortType, String](
    portTypeMap, portTypeMap.map(_.swap)
  )

  val parameterParentTypeMap = Map(
    ParameterParentType.Escalation -> "escalation",
    ParameterParentType.Sla -> "sla"
  )
  implicit val parameterParentTypeColumnTypeMapper = MappedColumnType.base[ParameterParentType, String](
    parameterParentTypeMap, parameterParentTypeMap.map(_.swap)
  )

  val portParentTypeMap = Map(
    PortParentType.Breed -> "breed",
    PortParentType.BlueprintEndpoint -> "blueprint_endpoint",
    PortParentType.BlueprintParameter -> "blueprint_parameter",
    PortParentType.Deployment -> "deployment_endpoint"
  )
  implicit val portParentTypeColumnTypeMapper = MappedColumnType.base[PortParentType, String](
    portParentTypeMap, portParentTypeMap.map(_.swap)
  )

  val traitParameterParentTypeMap = Map(
    TraitParameterParentType.Blueprint -> "Blueprint",
    TraitParameterParentType.Deployment -> "Deployment"
  )

  implicit val traitParameterParentTypeColumnTypeMapper = MappedColumnType.base[TraitParameterParentType, String](
    traitParameterParentTypeMap, traitParameterParentTypeMap.map(_.swap)
  )

  val deploymentStateTypeMap = Map(
    DeploymentStateType.ReadyForDeployment -> "ReadyForDeployment",
    DeploymentStateType.Deployed -> "Deployed",
    DeploymentStateType.ReadyForUndeployment -> "ReadyForUndeployment",
    DeploymentStateType.Error -> "Error"
  )

  implicit val deploymentStateTypeColumnTypeMapper = MappedColumnType.base[DeploymentStateType, String](
    deploymentStateTypeMap, deploymentStateTypeMap.map(_.swap)
  )

  implicit def deploymentServiceState2DeploymentStateType(state: DeploymentService.State): DeploymentStateType = state match {
    case _: ReadyForDeployment => DeploymentStateType.ReadyForDeployment
    case _: Deployed => DeploymentStateType.Deployed
    case _: ReadyForUndeployment => DeploymentStateType.ReadyForDeployment
    case _: Error => DeploymentStateType.Error
  }


  implicit def deploymentService2deploymentState(deploymentService: DeploymentServiceModel): State =
    deploymentService.deploymentState match {
      case DeploymentStateType.ReadyForDeployment => ReadyForDeployment(startedAt = deploymentService.deploymentTime)
      case DeploymentStateType.Deployed => Deployed(startedAt = deploymentService.deploymentTime)
      case DeploymentStateType.ReadyForUndeployment => ReadyForUndeployment(startedAt = deploymentService.deploymentTime)
      case DeploymentStateType.Error => Error(startedAt = deploymentService.deploymentTime, notification = PersistenceOperationFailure(deploymentService.message))
    }

  val parameterTypeMap = Map(
    ParameterType.String -> "String",
    ParameterType.Int -> "Int",
    ParameterType.Double -> "Double"
  )
  implicit val parameterTypeColumnTypeMapper = MappedColumnType.base[ParameterType, String](
    parameterTypeMap, parameterTypeMap.map(_.swap)
  )

  val traitNameParameterGroupTypeMap = Map(
    Trait.Name.Group.EnvironmentVariables -> "environment_variables",
    Trait.Name.Group.Ports -> "ports"
  )
  implicit val traitNameParameterGroupTypeMapper = MappedColumnType.base[Trait.Name.Group.Value, String](
    traitNameParameterGroupTypeMap, traitNameParameterGroupTypeMap.map(_.swap)
  )

  val environmentVariableParentTypeMap = Map(
    EnvironmentVariableParentType.Breed -> "breed",
    EnvironmentVariableParentType.BlueprintParameter -> "blueprint_parameter"
  )
  implicit val environmentVariableParentTypeMapper = MappedColumnType.base[EnvironmentVariableParentType, String](
    environmentVariableParentTypeMap, environmentVariableParentTypeMap.map(_.swap)
  )

  implicit def deployment2Model(a: Deployment): DeploymentModel =
    DeploymentModel(name = a.name)

  implicit def defaultBlueprint2Model(a: DeploymentDefaultBlueprint): DefaultBlueprintModel =
    DefaultBlueprintModel(deploymentId = a.deploymentId, name = a.artifact.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.artifact.name))

  implicit def genericEscalation2Model(a: DeploymentGenericEscalation): GenericEscalationModel =
    GenericEscalationModel(deploymentId = a.deploymentId, name = a.artifact.name, escalationType = a.artifact.`type`, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.artifact.name))

  implicit def defaultFilterModel2Artifact(m: DefaultFilterModel): DefaultFilter =
    DefaultFilter(name = VampPersistenceUtil.restoreToAnonymous(m.name, m.isAnonymous), condition = m.condition)

  implicit def defaultFilter2Model(a: DeploymentDefaultFilter): DefaultFilterModel =
    DefaultFilterModel(deploymentId = a.deploymentId, condition = a.artifact.condition, name = a.artifact.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.artifact.name))

  implicit def defaultRouting2Model(a: DeploymentDefaultRouting): DefaultRoutingModel =
    DefaultRoutingModel(deploymentId = a.deploymentId, weight = a.artifact.weight, name = a.artifact.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.artifact.name))

  implicit def defaultScaleModel2Artifact(m: DefaultScaleModel): DefaultScale =
    DefaultScale(cpu = m.cpu, memory = m.memory, instances = m.instances, name = VampPersistenceUtil.restoreToAnonymous(m.name, m.isAnonymous))

  implicit def defaultScale2Model(a: DeploymentDefaultScale): DefaultScaleModel =
    DefaultScaleModel(deploymentId = a.deploymentId, cpu = a.artifact.cpu, memory = a.artifact.memory, instances = a.artifact.instances, name = a.artifact.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.artifact.name))

  implicit def genericSla2Model(a: DeploymentGenericSla): GenericSlaModel =
    GenericSlaModel(deploymentId = a.deploymentId, name = a.artifact.name, slaType = a.artifact.`type`, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.artifact.name))

  implicit def defaultBreed2Model(a: DeploymentDefaultBreed): DefaultBreedModel =
    DefaultBreedModel(deploymentId = a.deploymentId, deployable = a.artifact.deployable.name, name = a.artifact.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.artifact.name))

  implicit def environmentVariableModel2Artifact(m: EnvironmentVariableModel): EnvironmentVariable =
    EnvironmentVariable(name = m.name, alias = m.alias, value = m.value, direction = m.direction)

  implicit def portModel2Port(model: PortModel): Port = model.portType match {
    case PortType.HTTP => HttpPort(model.name, model.alias, model.value, model.direction)
    case PortType.TCP => TcpPort(model.name, model.alias, model.value, model.direction)
    case _ => throw new RuntimeException(s"Handler for this portType: ${model.portType} is not implemented")
  }

  implicit def port2PortModel(port: Port): PortModel =
    port match {
      case TcpPort(_, _, _, _) => PortModel(name = port.name.value, alias = port.alias, portType = PortType.TCP, value = port.value, direction = port.direction)
      case HttpPort(_, _, _, _) => PortModel(name = port.name.value, alias = port.alias, portType = PortType.HTTP, value = port.value, direction = port.direction)
      case _ => throw new RuntimeException(s"Handler for portType not implemented")
    }

  implicit val offsetDateTimeColumnTypeMapper = MappedColumnType.base[OffsetDateTime, String](
  { s => s.toString }, { c => OffsetDateTime.parse(c) }
  )


}
