package io.magnetic.vamp_core.persistence.slick.model

import java.sql.Timestamp
import java.time.{OffsetDateTime, LocalDateTime}

import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.persistence.slick.extension.{NamedDeployable, AnonymousDeployable, Nameable}
import io.magnetic.vamp_core.persistence.slick.model.DeploymentStateType.DeploymentStateType
import io.magnetic.vamp_core.persistence.slick.model.EnvironmentVariableParentType.EnvironmentVariableParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterType.ParameterType
import io.magnetic.vamp_core.persistence.slick.model.PortParentType.PortParentType
import io.magnetic.vamp_core.persistence.slick.model.PortType.PortType
import io.magnetic.vamp_core.persistence.slick.util.VampPersistenceUtil
import io.strongtyped.active.slick.models.Identifiable

trait VampPersistenceModel[E <: io.strongtyped.active.slick.models.Identifiable[E]] extends Identifiable[E] {
  type Id = Int // Default is using Int as our id column type
}

trait VampNameablePersistenceModel[E <: Nameable[E]] extends VampPersistenceModel[E] with Nameable[E]

trait VampDeployablePersistenceModel[E <: NamedDeployable[E]] extends VampNameablePersistenceModel[E] with NamedDeployable[E]

trait VampAnonymousNameablePersistenceModel[E <: AnonymousDeployable[E]] extends VampDeployablePersistenceModel[E] with AnonymousDeployable[E]

case class VampPersistenceMetaDataModel(id: Option[Int] = None, schemaVersion : Int, created : Timestamp = Timestamp.valueOf(LocalDateTime.now())) extends VampPersistenceModel[VampPersistenceMetaDataModel] {
  override def withId(id: Id): VampPersistenceMetaDataModel = copy(id = Option(id))
}

case class DefaultBlueprintModel(deploymentId : Option[Int], name: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultBlueprintModel] {
  override def withId(id: Id): DefaultBlueprintModel = copy(id = Option(id))

  override def withAnonymousName: DefaultBlueprintModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class BlueprintReferenceModel(deploymentId : Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[BlueprintReferenceModel] {
  override def withId(id: Id): BlueprintReferenceModel = copy(id = Option(id))
}

case class ClusterModel(deploymentId : Option[Int], name: String, blueprintId: Int, slaReference: Option[String], id: Option[Int] = None) extends VampDeployablePersistenceModel[ClusterModel] {
  override def withId(id: Id): ClusterModel = copy(id = Option(id))
}

case class ServiceModel(deploymentId : Option[Int], clusterId: Int, breedReferenceName: String, routingReferenceName: Option[String], scaleReferenceName: Option[String], id: Option[Int] = None) extends VampPersistenceModel[ServiceModel] {
  override def withId(id: Id): ServiceModel = copy(id = Option(id))
}

case class SlaReferenceModel(deploymentId : Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[SlaReferenceModel] {
  override def withId(id: Id): SlaReferenceModel = copy(id = Option(id))
}

case class DefaultSlaModel(deploymentId : Option[Int], name: String, slaType: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultSlaModel] {
  override def withId(id: Id): DefaultSlaModel = copy(id = Option(id))

  override def withAnonymousName: DefaultSlaModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class EscalationReferenceModel(deploymentId : Option[Int], name: String, slaId: Option[Int], slaRefId: Option[Int], id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[EscalationReferenceModel] {
  override def withId(id: Id): EscalationReferenceModel = copy(id = Option(id))
}

case class DefaultEscalationModel(deploymentId : Option[Int], name: String, escalationType: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultEscalationModel] {
  override def withId(id: Id): DefaultEscalationModel = copy(id = Option(id))

  override def withAnonymousName: DefaultEscalationModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class ScaleReferenceModel(deploymentId : Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[ScaleReferenceModel] {
  override def withId(id: Id): ScaleReferenceModel = copy(id = Option(id))
}

case class DefaultScaleModel(deploymentId : Option[Int], name: String, cpu: Double, memory: Double, instances: Int, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultScaleModel] {
  override def withId(id: Id): DefaultScaleModel = copy(id = Option(id))

  override def withAnonymousName: DefaultScaleModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class RoutingReferenceModel(deploymentId : Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[RoutingReferenceModel] {
  override def withId(id: Id): RoutingReferenceModel = copy(id = Option(id))
}

case class DefaultRoutingModel(deploymentId : Option[Int], name: String, weight: Option[Int], id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultRoutingModel] {
  override def withId(id: Id): DefaultRoutingModel = copy(id = Option(id))

  override def withAnonymousName: DefaultRoutingModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class FilterReferenceModel(deploymentId : Option[Int], name: String, id: Option[Int] = None, routingId: Int, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[FilterReferenceModel] {
  override def withId(id: Id): FilterReferenceModel = copy(id = Option(id))
}

case class DefaultFilterModel(deploymentId : Option[Int], name: String, condition: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultFilterModel] {
  override def withId(id: Id): DefaultFilterModel = copy(id = Option(id))

  override def withAnonymousName: DefaultFilterModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class BreedReferenceModel(deploymentId : Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[BreedReferenceModel] {
  override def withId(id: Id): BreedReferenceModel = copy(id = Option(id))
}

case class DefaultBreedModel(deploymentId : Option[Int], name: String, deployable: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultBreedModel] {
  override def withId(id: Id): DefaultBreedModel = copy(id = Option(id))

  override def withAnonymousName: DefaultBreedModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class PortModel(deploymentId : Option[Int],name: String, alias: Option[String], portType: PortType, value: Option[Int], direction: Trait.Direction.Value, id: Option[Int] = None, parentId: Option[Int] = None, parentType: Option[PortParentType] = None) extends VampDeployablePersistenceModel[PortModel] {
  override def withId(id: Id): PortModel = copy(id = Option(id))
}

case class EnvironmentVariableModel(deploymentId : Option[Int],name: String, alias: Option[String], value: Option[String], direction: Trait.Direction.Value, id: Option[Int] = None, parentId: Option[Int], parentType: Option[EnvironmentVariableParentType]) extends VampDeployablePersistenceModel[EnvironmentVariableModel] {
  override def withId(id: Id): EnvironmentVariableModel = copy(id = Option(id))
}

case class DependencyModel(deploymentId : Option[Int], name: String, breedName: String, id: Option[Int] = None, isDefinedInline: Boolean, parentId: Int) extends VampDeployablePersistenceModel[DependencyModel] {
  override def withId(id: Id): DependencyModel = copy(id = Option(id))
}

case class ParameterModel(deploymentId : Option[Int],name: String, stringValue: Option[String] = None, intValue: Int = 0, doubleValue: Double = 0, parameterType: ParameterType, id: Option[Int] = None, parentType: ParameterParentType, parentId : Int) extends VampDeployablePersistenceModel[ParameterModel] {
  override def withId(id: Id): ParameterModel = copy(id = Option(id))
}

case class TraitNameParameterModel(deploymentId : Option[Int],id: Option[Int] = None, name: String, scope: Option[String], groupType: Option[Trait.Name.Group.Value], stringValue: Option[String] = None, groupId: Option[Int] = None, parentId: Int) extends VampDeployablePersistenceModel[TraitNameParameterModel] {
  override def withId(id: Id): TraitNameParameterModel = copy(id = Option(id))
}

trait DeployableArtifact  {
  def deploymentId : Option[Int]
  def artifact : Artifact
}

case class DeploymentModel(id: Option[Int] = None,name: String) extends VampNameablePersistenceModel[DeploymentModel] {
  override def withId(id: Id): DeploymentModel = copy(id = Option(id))
}

case class DeploymentClusterModel(deploymentId : Option[Int],id: Option[Int] = None,name: String, slaReference : Option[String])extends VampDeployablePersistenceModel[DeploymentClusterModel] {
override def withId(id: Id): DeploymentClusterModel = copy(id = Option(id))
}

case class DeploymentServiceModel(deploymentId : Option[Int], clusterId : Int, id: Option[Int] = None, name: String, breed: Int, scale: Option[Int], routing: Option[Int], deploymentState : DeploymentStateType, deploymentTime : OffsetDateTime, message : Option[String] = None)
  extends VampDeployablePersistenceModel[DeploymentServiceModel] {
override def withId(id: Id): DeploymentServiceModel = copy(id = Option(id))
}

case class DeploymentServerModel(deploymentId : Option[Int],serviceId : Int, id: Option[Int] = None, name: String, host: String, deployed: Boolean)  extends VampDeployablePersistenceModel[DeploymentServerModel] {
  override def withId(id: Id): DeploymentServerModel = copy(id = Option(id))
}

case class ServerPortModel(id: Option[Int] = None, portIn : Int, portOut : Int, serverId : Int) extends VampPersistenceModel[ServerPortModel] {
  override def withId(id: Id): ServerPortModel = copy(id = Option(id))
}

case class DeploymentServiceDependencyModel(id: Option[Int] = None, name : String, value : String, serviceId : Int) extends VampPersistenceModel[DeploymentServiceDependencyModel] {
  override def withId(id: Id): DeploymentServiceDependencyModel = copy(id = Option(id))
}

case class ClusterRouteModel(id: Option[Int] = None, portIn : Int, portOut : Int, clusterId : Int) extends VampPersistenceModel[ClusterRouteModel] {
  override def withId(id: Id): ClusterRouteModel = copy(id = Option(id))
}

case class DeploymentDefaultFilter(deploymentId : Option[Int], artifact: DefaultFilter)
case class DeploymentDefaultSla(deploymentId : Option[Int], artifact: GenericSla)
case class DeploymentDefaultScale(deploymentId : Option[Int], artifact: DefaultScale)
case class DeploymentDefaultEscalation(deploymentId : Option[Int], artifact: GenericEscalation)
case class DeploymentDefaultRouting(deploymentId : Option[Int], artifact: DefaultRouting)
case class DeploymentDefaultBreed(deploymentId : Option[Int], artifact: DefaultBreed)
case class DeploymentDefaultBlueprint(deploymentId : Option[Int], artifact: DefaultBlueprint)