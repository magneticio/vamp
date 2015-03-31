package io.vamp.core.persistence.store.jdbc

import com.typesafe.scalalogging.Logger
import io.vamp.core.model.artifact.Trait
import io.vamp.core.model.artifact.Trait.Name
import io.vamp.core.persistence.notification.PersistenceNotificationProvider
import io.vamp.core.persistence.slick.model.TraitNameParameterModel
import io.vamp.core.persistence.slick.model.TraitParameterParentType._
import org.slf4j.LoggerFactory

import scala.slick.jdbc.JdbcBackend


trait TraitNameParameterStore extends PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._

  private val logger = Logger(LoggerFactory.getLogger(classOf[TraitNameParameterStore]))

  protected def createTraitNameParameters(parameters: Map[Trait.Name, Any], parentId: Option[Int], parentType: TraitParameterParentType): Unit = {
    for (param <- parameters) {
      val prefilledParameter = TraitNameParameterModel(name = param._1.value, scope = param._1.scope, parentId = parentId, groupType = param._1.group, parentType = parentType)
      TraitNameParameters.add(
        param._2 match {
          case value: String =>
            prefilledParameter.copy(stringValue = Some(value))
          case value: Int =>
            prefilledParameter.copy(intValue = Some(value))
          case value =>
            logger.warn(s"Incorrect trait name parameter of type ${value.getClass} found; storing it as a string value.")
            prefilledParameter.copy(stringValue = Some(value.toString))
        }
      )
    }
  }

  protected def traitNameParametersToArtifactMap(traitNames: List[TraitNameParameterModel]): Map[Trait.Name, Any] = (
    for {traitName <- traitNames
         restoredArtifact: Any = traitName.groupType match {
           case Some(group) if group == Trait.Name.Group.Ports =>
             traitName.intValue.getOrElse(0)
           case Some(group) if group == Trait.Name.Group.EnvironmentVariables =>
             traitName.stringValue.getOrElse("")
           case _ =>
             traitName.stringValue.getOrElse("")
         }
    } yield Name(scope = traitName.scope, group = traitName.groupType, value = traitName.name) -> restoredArtifact).toMap

  protected def deleteModelTraitNameParameters(params: List[TraitNameParameterModel]): Unit =
    for (p <- params) {
      TraitNameParameters.deleteById(p.id.get)
    }


}
