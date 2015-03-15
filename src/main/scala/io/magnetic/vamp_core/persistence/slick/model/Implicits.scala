package io.magnetic.vamp_core.persistence.slick.model

import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterType.ParameterType
import io.magnetic.vamp_core.persistence.slick.model.PortType.PortType
import io.magnetic.vamp_core.persistence.slick.util.VampPersistenceUtil

import scala.language.implicitConversions
import scala.slick.driver.JdbcDriver.simple._

/**
 * Implicit conversions for Slick columns
 */
object Implicits {

  implicit val traitDirectionMapper = MappedColumnType.base[Trait.Direction.Value, String](
  { c => c.toString}, { s => Trait.Direction.withName(s)}
  )

  implicit val dependencyTypeMapper = MappedColumnType.base[DependencyType.Value, String](
  { c => c.toString}, { s => DependencyType.withName(s)}
  )

  val portTypeMap = Map(
    PortType.HTTP -> "http",
    PortType.TCP -> "tcp"
  )
  implicit val portTypeColumnTypeMapper = MappedColumnType.base[PortType, String](
    portTypeMap, portTypeMap.map(_.swap)
  )

  val parentParameterTypeMap = Map(
    ParameterParentType.Blueprint -> "blueprint",
    ParameterParentType.Escalation -> "escalation",
    ParameterParentType.Sla -> "sla"
  )
  implicit val parentTypeColumnTypeMapper = MappedColumnType.base[ParameterParentType, String](
    parentParameterTypeMap, parentParameterTypeMap.map(_.swap)
  )

  val parameterTypeMap = Map(
    ParameterType.String -> "String",
    ParameterType.Int -> "Int",
    ParameterType.Double -> "Double"
  )
  implicit val parameterTypeColumnTypeMapper = MappedColumnType.base[ParameterType, String](
    parameterTypeMap, parameterTypeMap.map(_.swap)
  )

  implicit def defaultBlueprint2Model(a: DefaultBlueprint): DefaultBlueprintModel =
    DefaultBlueprintModel(name = a.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.name))

  implicit def defaultEscalation2Model(artifact: DefaultEscalation): DefaultEscalationModel =
    DefaultEscalationModel(name = artifact.name, escalationType = artifact.`type`, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(artifact.name))

  implicit def defaultFilterModel2Artifact(m: DefaultFilterModel): DefaultFilter =
    DefaultFilter(name = VampPersistenceUtil.restoreToAnonymous(m.name, m.isAnonymous), condition = m.condition)

  implicit def defaultFilter2Model(a: DefaultFilter): DefaultFilterModel =
    DefaultFilterModel(condition = a.condition, name = a.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.name))

  implicit def defaultRouting2Model(a: DefaultRouting): DefaultRoutingModel =
    DefaultRoutingModel(weight = a.weight, name = a.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.name))

  implicit def defaultScaleModel2Artifact(m: DefaultScaleModel): DefaultScale =
    DefaultScale(cpu = m.cpu, memory = m.memory, instances = m.instances, name = VampPersistenceUtil.restoreToAnonymous(m.name, m.isAnonymous))

  implicit def defaultScale2Model(a: DefaultScale): DefaultScaleModel =
    DefaultScaleModel(cpu = a.cpu, memory = a.memory, instances = a.instances, name = a.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.name))

  implicit def defaultSla2Model(artifact: DefaultSla): DefaultSlaModel =
    DefaultSlaModel(name = artifact.name, slaType = artifact.`type`, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(artifact.name))

  implicit def defaultBreed2Model(a: DefaultBreed): DefaultBreedModel =
    DefaultBreedModel(deployable = a.deployable.name, name = a.name, isAnonymous = VampPersistenceUtil.matchesCriteriaForAnonymous(a.name))

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
}
