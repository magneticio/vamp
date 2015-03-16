package io.magnetic.vamp_core.persistence.slick.model

import io.magnetic.vamp_core.model.artifact.Trait
import io.magnetic.vamp_core.persistence.slick.extension.{AnonymousNameable, Nameable}
import io.magnetic.vamp_core.persistence.slick.model.EnvironmentVariableParentType.EnvironmentVariableParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterType.ParameterType
import io.magnetic.vamp_core.persistence.slick.model.PortParentType.PortParentType
import io.magnetic.vamp_core.persistence.slick.model.PortType.PortType
import io.magnetic.vamp_core.persistence.slick.util.VampPersistenceUtil
import io.strongtyped.active.slick.models.Identifiable

trait VampPersistenceModel

trait VampPersistenceModelIdentifiable[E <: io.strongtyped.active.slick.models.Identifiable[E]] extends VampPersistenceModel with Identifiable[E] {
  type Id = Int // Default is using Int as our id column type
}

trait VampNameablePersistenceModel[E <: Nameable[E]] extends VampPersistenceModelIdentifiable[E] with Nameable[E]

trait VampAnonymousNameablePersistenceModel[E <: AnonymousNameable[E]] extends VampNameablePersistenceModel[E] with AnonymousNameable[E]


case class DefaultBlueprintModel(name: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultBlueprintModel] {
  override def withId(id: Id): DefaultBlueprintModel = copy(id = Option(id))

  override def withAnonymousName: DefaultBlueprintModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class BlueprintReferenceModel(name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampNameablePersistenceModel[BlueprintReferenceModel] {
  override def withId(id: Id): BlueprintReferenceModel = copy(id = Option(id))
}

case class ClusterModel(name: String, blueprintId: Int, slaReference: Option[String], id: Option[Int] = None) extends VampNameablePersistenceModel[ClusterModel] {
  override def withId(id: Id): ClusterModel = copy(id = Option(id))
}

case class ServiceModel(clusterId: Int, breedReferenceName: String, routingReferenceName: Option[String], scaleReferenceName: Option[String], id: Option[Int] = None) extends VampPersistenceModelIdentifiable[ServiceModel] {
  override def withId(id: Id): ServiceModel = copy(id = Option(id))
}

case class SlaReferenceModel(name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampNameablePersistenceModel[SlaReferenceModel] {
  override def withId(id: Id): SlaReferenceModel = copy(id = Option(id))
}

case class DefaultSlaModel(name: String, slaType: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultSlaModel] {
  override def withId(id: Id): DefaultSlaModel = copy(id = Option(id))

  override def withAnonymousName: DefaultSlaModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class EscalationReferenceModel(name: String, slaId: Option[Int], slaRefId: Option[Int], id: Option[Int] = None, isDefinedInline: Boolean) extends VampNameablePersistenceModel[EscalationReferenceModel] {
  override def withId(id: Id): EscalationReferenceModel = copy(id = Option(id))
}

case class DefaultEscalationModel(name: String, escalationType: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultEscalationModel] {
  override def withId(id: Id): DefaultEscalationModel = copy(id = Option(id))

  override def withAnonymousName: DefaultEscalationModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class ScaleReferenceModel(name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampNameablePersistenceModel[ScaleReferenceModel] {
  override def withId(id: Id): ScaleReferenceModel = copy(id = Option(id))
}

case class DefaultScaleModel(name: String, cpu: Double, memory: Double, instances: Int, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultScaleModel] {
  override def withId(id: Id): DefaultScaleModel = copy(id = Option(id))

  override def withAnonymousName: DefaultScaleModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class RoutingReferenceModel(name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampNameablePersistenceModel[RoutingReferenceModel] {
  override def withId(id: Id): RoutingReferenceModel = copy(id = Option(id))
}

case class DefaultRoutingModel(name: String, weight: Option[Int], id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultRoutingModel] {
  override def withId(id: Id): DefaultRoutingModel = copy(id = Option(id))

  override def withAnonymousName: DefaultRoutingModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class FilterReferenceModel(name: String, id: Option[Int] = None, routingId: Int, isDefinedInline: Boolean) extends VampNameablePersistenceModel[FilterReferenceModel] {
  override def withId(id: Id): FilterReferenceModel = copy(id = Option(id))
}

case class DefaultFilterModel(name: String, condition: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultFilterModel] {
  override def withId(id: Id): DefaultFilterModel = copy(id = Option(id))

  override def withAnonymousName: DefaultFilterModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class BreedReferenceModel(name: String, id: Option[Int] = None, isDefinedInline: Boolean) extends VampNameablePersistenceModel[BreedReferenceModel] {
  override def withId(id: Id): BreedReferenceModel = copy(id = Option(id))
}

case class DefaultBreedModel(name: String, deployable: String, id: Option[Int] = None, isAnonymous: Boolean = false) extends VampAnonymousNameablePersistenceModel[DefaultBreedModel] {
  override def withId(id: Id): DefaultBreedModel = copy(id = Option(id))

  override def withAnonymousName: DefaultBreedModel = copy(name = VampPersistenceUtil.generatedAnonymousName)
}

case class PortModel(name: String, alias: Option[String], portType: PortType, value: Option[Int], direction: Trait.Direction.Value, id: Option[Int] = None, parentId: Option[Int] = None, parentType: Option[PortParentType] = None) extends VampNameablePersistenceModel[PortModel] {
  override def withId(id: Id): PortModel = copy(id = Option(id))
}

case class EnvironmentVariableModel(name: String, alias: Option[String], value: Option[String], direction: Trait.Direction.Value, id: Option[Int] = None, parentId: Option[Int], parentType: Option[EnvironmentVariableParentType]) extends VampNameablePersistenceModel[EnvironmentVariableModel] {
  override def withId(id: Id): EnvironmentVariableModel = copy(id = Option(id))
}

case class DependencyModel(name: String, breedName: String, id: Option[Int] = None, isDefinedInline: Boolean, parentBreedName: String) extends VampNameablePersistenceModel[DependencyModel] {
  override def withId(id: Id): DependencyModel = copy(id = Option(id))
}

case class ParameterModel(name: String, stringValue: Option[String] = None, intValue: Int = 0, doubleValue: Double = 0, parameterType: ParameterType, id: Option[Int] = None, parentType: ParameterParentType, parentName: String) extends VampNameablePersistenceModel[ParameterModel] {
  override def withId(id: Id): ParameterModel = copy(id = Option(id))
}

case class TraitNameParameterModel(id: Option[Int] = None, name: String, scope: Option[String], groupType: Option[Trait.Name.Group.Value], stringValue: Option[String] = None, groupId: Option[Int] = None, parentId: Int) extends VampNameablePersistenceModel[TraitNameParameterModel] {
  override def withId(id: Id): TraitNameParameterModel = copy(id = Option(id))
}



