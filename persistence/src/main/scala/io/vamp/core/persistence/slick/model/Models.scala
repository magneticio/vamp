package io.vamp.core.persistence.slick.model

import java.sql.Timestamp
import java.time.{ LocalDateTime, OffsetDateTime }

import io.strongtyped.active.slick.models.Identifiable
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.slick.extension.{ AnonymousDeployable, Nameable, NamedDeployable }
import io.vamp.core.persistence.slick.model.ConstantParentType.ConstantParentType
import io.vamp.core.persistence.slick.model.DeploymentIntention.DeploymentIntentionType
import io.vamp.core.persistence.slick.model.DeploymentStep.DeploymentStepType
import io.vamp.core.persistence.slick.model.EnvironmentVariableParentType.EnvironmentVariableParentType
import io.vamp.core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.vamp.core.persistence.slick.model.ParameterType.ParameterType
import io.vamp.core.persistence.slick.model.PortParentType.PortParentType
import io.vamp.core.persistence.slick.util.VampPersistenceUtil

import scala.concurrent.duration.FiniteDuration

trait VampPersistenceModel[E <: io.strongtyped.active.slick.models.Identifiable[E]] extends Identifiable[E] {
  type Id = Int // Default is using Int as our id column type
}

trait VampNameablePersistenceModel[E <: Nameable[E]] extends VampPersistenceModel[E] with Nameable[E]

trait VampDeployablePersistenceModel[E <: NamedDeployable[E]] extends VampNameablePersistenceModel[E] with NamedDeployable[E]

trait VampAnonymousNameablePersistenceModel[E <: AnonymousDeployable[E]] extends VampDeployablePersistenceModel[E] with AnonymousDeployable[E]

case class VampPersistenceMetaDataModel(id: Option[Int] = None, schemaVersion: Int, created: Timestamp = Timestamp.valueOf(LocalDateTime.now())) extends VampPersistenceModel[VampPersistenceMetaDataModel] {
  override def withId(id: Id): VampPersistenceMetaDataModel = copy(id = Option(id))
}

case class DefaultBlueprintModel(deploymentId: Option[Int], name: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultBlueprintModel] {
  override def withId(id: Id): DefaultBlueprintModel = copy(id = Option(id))

  override def withAnonymousName: DefaultBlueprintModel = copy(name = VampPersistenceUtil.generateAnonymousName)
}

case class BlueprintReferenceModel(deploymentId: Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[BlueprintReferenceModel] {
  override def withId(id: Id): BlueprintReferenceModel = copy(id = Option(id))
}

case class ClusterModel(deploymentId: Option[Int], name: String, blueprintId: Int, slaReference: Option[Int], dialects: Array[Byte], id: Option[Int] = None) extends VampDeployablePersistenceModel[ClusterModel] {
  override def withId(id: Id): ClusterModel = copy(id = Option(id))
}

case class ServiceModel(deploymentId: Option[Int], clusterId: Int, breedReferenceId: Int, routingReference: Option[Int], scaleReference: Option[Int], dialects: Array[Byte], id: Option[Int] = None) extends VampPersistenceModel[ServiceModel] {
  override def withId(id: Id): ServiceModel = copy(id = Option(id))
}

case class SlaReferenceModel(deploymentId: Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[SlaReferenceModel] {
  override def withId(id: Id): SlaReferenceModel = copy(id = Option(id))
}

case class GenericSlaModel(deploymentId: Option[Int], name: String, slaType: String, id: Option[Int] = None,
                           upper: Option[FiniteDuration] = None, lower: Option[FiniteDuration] = None,
                           interval: Option[FiniteDuration] = None, cooldown: Option[FiniteDuration] = None,
                           isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[GenericSlaModel] {
  override def withId(id: Id): GenericSlaModel = copy(id = Option(id))

  override def withAnonymousName: GenericSlaModel = copy(name = VampPersistenceUtil.generateAnonymousName)
}

case class EscalationReferenceModel(deploymentId: Option[Int], name: String, slaId: Option[Int], slaRefId: Option[Int], parentEscalationId: Option[Int], id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[EscalationReferenceModel] {
  override def withId(id: Id): EscalationReferenceModel = copy(id = Option(id))
}

case class GenericEscalationModel(deploymentId: Option[Int], name: String, escalationType: String,
                                  minimumInt: Option[Int] = None, maximumInt: Option[Int] = None, scaleByInt: Option[Int] = None,
                                  minimumDouble: Option[Double] = None, maximumDouble: Option[Double] = None, scaleByDouble: Option[Double] = None,
                                  targetCluster: Option[String] = None,
                                  id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[GenericEscalationModel] {
  override def withId(id: Id): GenericEscalationModel = copy(id = Option(id))

  override def withAnonymousName: GenericEscalationModel = copy(name = VampPersistenceUtil.generateAnonymousName)
}

case class ScaleReferenceModel(deploymentId: Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[ScaleReferenceModel] {
  override def withId(id: Id): ScaleReferenceModel = copy(id = Option(id))
}

case class DefaultScaleModel(deploymentId: Option[Int], name: String, cpu: Double, memory: Double, instances: Int, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultScaleModel] {
  override def withId(id: Id): DefaultScaleModel = copy(id = Option(id))

  override def withAnonymousName: DefaultScaleModel = copy(name = VampPersistenceUtil.generateAnonymousName)
}

case class RoutingReferenceModel(deploymentId: Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[RoutingReferenceModel] {
  override def withId(id: Id): RoutingReferenceModel = copy(id = Option(id))
}

case class DefaultRoutingModel(deploymentId: Option[Int], name: String, weight: Option[Int], id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultRoutingModel] {
  override def withId(id: Id): DefaultRoutingModel = copy(id = Option(id))

  override def withAnonymousName: DefaultRoutingModel = copy(name = VampPersistenceUtil.generateAnonymousName)
}

case class FilterReferenceModel(deploymentId: Option[Int], name: String, id: Option[Int] = None, routingId: Int, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[FilterReferenceModel] {
  override def withId(id: Id): FilterReferenceModel = copy(id = Option(id))
}

case class DefaultFilterModel(deploymentId: Option[Int], name: String, condition: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultFilterModel] {
  override def withId(id: Id): DefaultFilterModel = copy(id = Option(id))

  override def withAnonymousName: DefaultFilterModel = copy(name = VampPersistenceUtil.generateAnonymousName)
}

case class BreedReferenceModel(deploymentId: Option[Int], name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampDeployablePersistenceModel[BreedReferenceModel] {
  override def withId(id: Id): BreedReferenceModel = copy(id = Option(id))
}

case class DefaultBreedModel(deploymentId: Option[Int], name: String, deployable: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultBreedModel] {
  override def withId(id: Id): DefaultBreedModel = copy(id = Option(id))

  override def withAnonymousName: DefaultBreedModel = copy(name = VampPersistenceUtil.generateAnonymousName)
}

case class PortModel(name: String, alias: Option[String], value: Option[String], id: Option[Int] = None, parentId: Option[Int] = None, parentType: Option[PortParentType] = None) extends VampNameablePersistenceModel[PortModel] {
  override def withId(id: Id): PortModel = copy(id = Option(id))
}

case class ConstantModel(name: String, alias: Option[String], value: Option[String], id: Option[Int] = None, parentId: Option[Int] = None, parentType: Option[ConstantParentType]) extends VampNameablePersistenceModel[ConstantModel] {
  override def withId(id: Id): ConstantModel = copy(id = Option(id))
}

case class EnvironmentVariableModel(deploymentId: Option[Int], name: String, alias: Option[String], value: Option[String], interpolated: Option[String], id: Option[Int] = None, parentId: Option[Int], parentType: Option[EnvironmentVariableParentType]) extends VampDeployablePersistenceModel[EnvironmentVariableModel] {
  override def withId(id: Id): EnvironmentVariableModel = copy(id = Option(id))
}

case class DependencyModel(deploymentId: Option[Int], name: String, breedName: String, id: Option[Int] = None, isDefinedInline: Boolean, parentId: Int) extends VampDeployablePersistenceModel[DependencyModel] {
  override def withId(id: Id): DependencyModel = copy(id = Option(id))
}

case class ParameterModel(deploymentId: Option[Int], name: String, stringValue: Option[String] = None, intValue: Int = 0, doubleValue: Double = 0, parameterType: ParameterType, id: Option[Int] = None, parentType: ParameterParentType, parentId: Int) extends VampDeployablePersistenceModel[ParameterModel] {
  override def withId(id: Id): ParameterModel = copy(id = Option(id))
}

case class DeploymentModel(id: Option[Int] = None, name: String) extends VampNameablePersistenceModel[DeploymentModel] {
  override def withId(id: Id): DeploymentModel = copy(id = Option(id))
}

case class DeploymentClusterModel(deploymentId: Option[Int], id: Option[Int] = None, name: String, slaReference: Option[Int], dialects: Array[Byte]) extends VampDeployablePersistenceModel[DeploymentClusterModel] {
  override def withId(id: Id): DeploymentClusterModel = copy(id = Option(id))
}

case class DeploymentServiceModel(deploymentId: Option[Int], clusterId: Int, id: Option[Int] = None, name: String, breed: Int, scale: Option[Int], routing: Option[Int], deploymentIntention: DeploymentIntentionType, deploymentStep: DeploymentStepType, deploymentTime: OffsetDateTime, deploymentStepTime: OffsetDateTime, dialects: Array[Byte], message: Option[String] = None)
    extends VampDeployablePersistenceModel[DeploymentServiceModel] {
  override def withId(id: Id): DeploymentServiceModel = copy(id = Option(id))
}

case class DeploymentServerModel(deploymentId: Option[Int], serviceId: Int, id: Option[Int] = None, name: String, host: String, deployed: Boolean) extends VampDeployablePersistenceModel[DeploymentServerModel] {
  override def withId(id: Id): DeploymentServerModel = copy(id = Option(id))
}

case class ServerPortModel(id: Option[Int] = None, portIn: Int, portOut: Int, serverId: Int) extends VampPersistenceModel[ServerPortModel] {
  override def withId(id: Id): ServerPortModel = copy(id = Option(id))
}

case class HostModel(id: Option[Int] = None, name: String, value: Option[String], deploymentId: Option[Int]) extends VampNameablePersistenceModel[HostModel] {
  override def withId(id: Id): HostModel = copy(id = Option(id))
}

case class DeploymentServiceDependencyModel(id: Option[Int] = None, name: String, value: String, serviceId: Int) extends VampPersistenceModel[DeploymentServiceDependencyModel] {
  override def withId(id: Id): DeploymentServiceDependencyModel = copy(id = Option(id))
}

case class ClusterRouteModel(id: Option[Int] = None, portIn: Int, portOut: Int, clusterId: Int) extends VampPersistenceModel[ClusterRouteModel] {
  override def withId(id: Id): ClusterRouteModel = copy(id = Option(id))
}

case class DeploymentDefaultFilter(deploymentId: Option[Int], artifact: DefaultFilter)

case class DeploymentGenericSla(deploymentId: Option[Int], artifact: Sla)

case class DeploymentDefaultScale(deploymentId: Option[Int], artifact: DefaultScale)

case class DeploymentGenericEscalation(deploymentId: Option[Int], artifact: Escalation)

case class DeploymentDefaultRouting(deploymentId: Option[Int], artifact: DefaultRouting)

case class DeploymentDefaultBreed(deploymentId: Option[Int], artifact: DefaultBreed)

case class DeploymentDefaultBlueprint(deploymentId: Option[Int], artifact: DefaultBlueprint)