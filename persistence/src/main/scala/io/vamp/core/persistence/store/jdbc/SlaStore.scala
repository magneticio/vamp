package io.vamp.core.persistence.store.jdbc

import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider}
import io.vamp.core.persistence.slick.model.{DeploymentGenericSla, GenericSlaModel, ParameterParentType, SlaReferenceModel}
import io.vamp.core.persistence.slick.util.VampPersistenceUtil

import scala.slick.jdbc.JdbcBackend

trait SlaStore extends EscalationStore with PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._

  protected def createSla(clusterSla: Option[Sla], deploymentId: Option[Int]): Option[Int] = clusterSla match {
    case Some(sla: SlaReference) =>
      val slaRefId = SlaReferences.add(SlaReferenceModel(deploymentId = deploymentId, name = sla.name, isDefinedInline = false))
      createEscalationReferences(sla.escalations, None, Some(slaRefId), deploymentId)
      Some(slaRefId)
    case Some(sla: Sla) =>
      val slaId = GenericSlas.findOptionByName(sla.name, deploymentId) match {
        case Some(existingSla) =>
          updateSla(existingSla, sla)
          existingSla.id
        case None =>
          createGenericSlaModelFromArtifact(DeploymentGenericSla(deploymentId, sla)).id
      }
      val storedSla = GenericSlas.findById(slaId.get)
      Some(SlaReferences.add(SlaReferenceModel(deploymentId = deploymentId, name = storedSla.name, isDefinedInline = true)))
    case _ => None  // Should never happen
  }

  protected def updateSla(existing: GenericSlaModel, a: Sla): Unit = {
    deleteSlaModelChildObjects(existing)
    createEscalationReferences(a.escalations, existing.id, None, existing.deploymentId)
    a match {
      case artifact: GenericSla =>
        createParameters(artifact.parameters, existing.id.get, ParameterParentType.Sla)
        existing.copy(slaType = artifact.`type`).update
      case artifact: ResponseTimeSlidingWindowSla =>
        existing.copy(slaType = "response_time_sliding_window", upper = Some(artifact.upper), lower = Some(artifact.lower), interval = Some(artifact.interval), cooldown = Some(artifact.cooldown)).update
      case artifact: EscalationOnlySla =>
        existing.copy(slaType = "escalation_only").update
    }
  }

  protected def deleteSlaFromDb(artifact: Sla): Unit = {
    GenericSlas.findOptionByName(artifact.name, None) match {
      case Some(sla) => deleteSlaModel(sla)
      case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
    }
  }

  protected def deleteSlaModel(sla: GenericSlaModel) {
    deleteSlaModelChildObjects(sla)
    GenericSlas.deleteById(sla.id.get)
  }

  protected def deleteSlaModelChildObjects(sla: GenericSlaModel): Unit = {
    for (escalationRef <- sla.escalationReferences) {
      GenericEscalations.findOptionByName(escalationRef.name, escalationRef.deploymentId) match {
        case Some(escalation) if escalation.isAnonymous => deleteEscalationModel(escalation)
        case Some(escalation) =>
        case None => // Should not happen
      }
      EscalationReferences.deleteById(escalationRef.id.get)
    }
    deleteExistingParameters(sla.parameters)
  }

  protected def findOptionSlaArtifactViaReferenceId(referenceId: Option[Int], deploymentId: Option[Int]): Option[Sla] = referenceId match {
    case Some(refId) =>
      SlaReferences.findOptionById(refId) match {
        case Some(slaReference) if slaReference.isDefinedInline =>
          findSlaOptionArtifact(slaReference.name, deploymentId) match {
            case Some(slaArtifact: EscalationOnlySla) => Some(slaArtifact)
            case Some(slaArtifact: ResponseTimeSlidingWindowSla) => Some(slaArtifact)
            case Some(slaArtifact: GenericSla) => Some(slaArtifact)
            case Some(slaArtifact: SlaReference) => Some(slaArtifact)
            case _ => None
          }
        case Some(slaReference) =>
          Some(SlaReference(name = slaReference.name, escalations = escalations2Artifacts(slaReference.escalationReferences)))
        case None => None
      }
    case None => None
  }

  protected def findSlaOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    GenericSlas.findOptionByName(name, defaultDeploymentId) match {
      case Some(s) => s.slaType match {
        case "escalation_only" =>
          Some(EscalationOnlySla(name = VampPersistenceUtil.restoreToAnonymous(s.name, s.isAnonymous), escalations = escalations2Artifacts(s.escalationReferences)))
        case "response_time_sliding_window" =>
          Some(ResponseTimeSlidingWindowSla(name = VampPersistenceUtil.restoreToAnonymous(s.name, s.isAnonymous), escalations = escalations2Artifacts(s.escalationReferences),
            upper = s.upper.get, lower = s.lower.get, interval = s.interval.get, cooldown = s.cooldown.get))
        case _ =>
          Some(GenericSla(name = VampPersistenceUtil.restoreToAnonymous(s.name, s.isAnonymous), `type` = s.slaType, escalations = escalations2Artifacts(s.escalationReferences), parameters = parametersToArtifact(s.parameters)))
      }
      case None => None
    }
  }

  protected def createSlaArtifact(art: Sla): String = art match {
    case a: EscalationOnlySla => createGenericSlaModelFromArtifact(DeploymentGenericSla(None, a)).name
    case a: ResponseTimeSlidingWindowSla => createGenericSlaModelFromArtifact(DeploymentGenericSla(None, a)).name
    case a: GenericSla => createGenericSlaModelFromArtifact(DeploymentGenericSla(None, a)).name
  }

  private def createGenericSlaModelFromArtifact(a: DeploymentGenericSla): GenericSlaModel = {
    val storedSlaId = GenericSlas.add(a)
    a.artifact match {
      case artifact: GenericSla =>
        createParameters(artifact.parameters, storedSlaId, ParameterParentType.Sla)
      case _ =>
    }
    createEscalationReferences(escalations = a.artifact.escalations, slaId = Some(storedSlaId), slaRefId = None, a.deploymentId)
    GenericSlas.findById(storedSlaId)
  }


}
