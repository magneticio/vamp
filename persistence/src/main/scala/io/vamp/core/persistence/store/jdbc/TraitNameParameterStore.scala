package io.vamp.core.persistence.store.jdbc

import io.vamp.core.model.artifact.Trait.Name
import io.vamp.core.model.artifact.{EnvironmentVariable, Port, Trait}
import io.vamp.core.persistence.notification.{PersistenceNotificationProvider, PersistenceOperationFailure}
import io.vamp.core.persistence.slick.model.TraitParameterParentType._
import io.vamp.core.persistence.slick.model.{EnvironmentVariableModel, EnvironmentVariableParentType, PortParentType, TraitNameParameterModel}

import scala.slick.jdbc.JdbcBackend


trait TraitNameParameterStore extends PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._


  protected def createTraitNameParameters(parameters: Map[Trait.Name, Any], parentId: Option[Int], parentType: TraitParameterParentType): Unit = {
    val deploymentId = None
    for (param <- parameters) {
      val prefilledParameter = TraitNameParameterModel(name = param._1.value, scope = param._1.scope, parentId = parentId, groupType = param._1.group, parentType = parentType)
      param._1.group match {
        case Some(group) if group == Trait.Name.Group.Ports =>
          param._2 match {
            case port: Port =>
              TraitNameParameters.add(prefilledParameter.copy(groupId = Some(Ports.add(port2PortModel(port).copy(parentType = Some(PortParentType.BlueprintParameter), parentId = parentId)))))
            case env =>
              // Not going to work, if the group is port, the parameter should be too
              throw exception(PersistenceOperationFailure(s"Parameter [${param._1.value}}] of type [${param._1.group}] does not match the supplied parameter [${param._2}}]."))
          }
        case Some(group) if group == Trait.Name.Group.EnvironmentVariables =>
          param._2 match {
            case env: EnvironmentVariable =>
              TraitNameParameters.add(prefilledParameter.copy(
                groupId = Some(EnvironmentVariables.add(
                  EnvironmentVariableModel(
                    deploymentId = deploymentId,
                    name = env.name.value,
                    alias = env.alias,
                    direction = env.direction,
                    value = env.value,
                    parentId = parentId,
                    parentType = Some(EnvironmentVariableParentType.BlueprintParameter))
                )
                )))
            case env =>
              // Not going to work, if the group is EnvironmentVariable, the parameter should be too
              throw exception(PersistenceOperationFailure(s"Parameter [${param._1.value}}] of type [${param._1.group}] does not match the supplied parameter [${param._2}}]."))

          }
        case None =>
          TraitNameParameters.add(
            param._2 match {
              case value: String =>
                prefilledParameter.copy(stringValue = Some(value))
              case value =>
                // Seems incorrect, store the value as a string
                prefilledParameter.copy(stringValue = Some(value.toString))
            }
          )
      }
    }
  }

  protected def traitNameParametersToArtifactMap(traitNames: List[TraitNameParameterModel]): Map[Trait.Name, Any] = (
    for {traitName <- traitNames
         restoredArtifact: Any = traitName.groupType match {
           case Some(group) if group == Trait.Name.Group.Ports =>
             portModel2Port(Ports.findById(traitName.groupId.get))
           case Some(group) if group == Trait.Name.Group.EnvironmentVariables =>
             environmentVariableModel2Artifact(EnvironmentVariables.findById(traitName.groupId.get))
           case _ =>
             traitName.stringValue.getOrElse("")
         }
    } yield Name(scope = traitName.scope, group = traitName.groupType, value = traitName.name) -> restoredArtifact).toMap

  protected def deleteModelTraitNameParameters(params: List[TraitNameParameterModel]): Unit =
    for (p <- params) {
      p.groupType match {
        case Some(env: Trait.Name.Group.EnvironmentVariables.type) =>
          EnvironmentVariables.deleteById(p.groupId.get)
        case Some(ports: Trait.Name.Group.Ports.type) =>
          Ports.deleteById(p.groupId.get)
        case _ =>
      }
      TraitNameParameters.deleteById(p.id.get)
    }


}
