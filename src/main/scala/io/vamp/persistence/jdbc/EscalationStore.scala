package io.vamp.persistence.jdbc

import io.vamp.model.artifact._
import io.vamp.persistence.notification.{ ArtifactNotFound, PersistenceNotificationProvider }
import io.vamp.persistence.slick.model.{ DeploymentGenericEscalation, EscalationReferenceModel, GenericEscalationModel, ParameterParentType }
import io.vamp.persistence.slick.util.{ Constants, VampPersistenceUtil }

import scala.slick.jdbc.JdbcBackend

trait EscalationStore extends ParameterStore with PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.persistence.slick.components.Components.instance._
  import io.vamp.persistence.slick.model.Implicits._

  protected def createEscalationReferences(escalations: List[Escalation], slaId: Option[Int], slaRefId: Option[Int], parentEscalationId: Option[Int], deploymentId: Option[Int]): Unit = {
    for (escalation ← escalations) {
      escalation match {
        case e: EscalationReference ⇒
          EscalationReferences.add(EscalationReferenceModel(deploymentId = deploymentId, name = e.name, slaId = slaId, slaRefId = slaRefId, parentEscalationId = parentEscalationId, isDefinedInline = false))
        case e ⇒
          val storedEscalation = GenericEscalations.findOptionByName(e.name, deploymentId) match {
            case Some(existing) ⇒
              updateEscalation(DeploymentGenericEscalation(deploymentId, e))
              existing
            case None ⇒
              createEscalationFromArtifact(DeploymentGenericEscalation(deploymentId, e))
          }
          EscalationReferences.add(EscalationReferenceModel(deploymentId = deploymentId, name = storedEscalation.name, slaId = slaId, slaRefId = slaRefId, parentEscalationId = parentEscalationId, isDefinedInline = true))
      }
    }
  }

  protected def updateEscalation(a: DeploymentGenericEscalation): Unit = {
    val existing = GenericEscalations.findByName(a.artifact.name, a.deploymentId)
    deleteExistingParameters(existing.parameters)
    a.artifact match {
      case artifact: GenericEscalation ⇒
        createParameters(artifact.parameters, existing.id.get, ParameterParentType.Escalation)
        existing.copy(escalationType = artifact.`type`).update
      case artifact: ScaleInstancesEscalation ⇒
        existing.copy(escalationType = Constants.Escalation_Scale_Instances, minimumInt = Some(artifact.minimum), maximumInt = Some(artifact.maximum), scaleByInt = Some(artifact.scaleBy), targetCluster = artifact.targetCluster).update
      case artifact: ScaleCpuEscalation ⇒
        existing.copy(escalationType = Constants.Escalation_Scale_Cpu, minimumDouble = Some(artifact.minimum), maximumDouble = Some(artifact.maximum), scaleByDouble = Some(artifact.scaleBy), targetCluster = artifact.targetCluster).update
      case artifact: ScaleMemoryEscalation ⇒
        existing.copy(escalationType = Constants.Escalation_Scale_Memory, minimumDouble = Some(artifact.minimum), maximumDouble = Some(artifact.maximum), scaleByDouble = Some(artifact.scaleBy), targetCluster = artifact.targetCluster).update
      case artifact: ToAllEscalation ⇒
        existing.copy(escalationType = Constants.Escalation_To_all).update
        deleteChildEscalations(existing.escalationReferences)
        createEscalationReferences(artifact.escalations, None, None, existing.id, a.deploymentId)
      case artifact: ToOneEscalation ⇒
        existing.copy(escalationType = Constants.Escalation_To_One).update
        deleteChildEscalations(existing.escalationReferences)
        createEscalationReferences(artifact.escalations, None, None, existing.id, a.deploymentId)
    }
  }

  protected def createEscalationFromArtifact(a: DeploymentGenericEscalation): GenericEscalationModel = {
    val storedEscalation = GenericEscalations.findById(GenericEscalations.add(a))
    a.artifact match {
      case artifact: GenericEscalation ⇒ createParameters(artifact.parameters, storedEscalation.id.get, ParameterParentType.Escalation)
      case artifact: ToAllEscalation ⇒
        createEscalationReferences(artifact.escalations, None, None, storedEscalation.id, a.deploymentId)
      case artifact: ToOneEscalation ⇒
        createEscalationReferences(artifact.escalations, None, None, storedEscalation.id, a.deploymentId)
      case _ ⇒
    }
    storedEscalation
  }

  protected def deleteEscalationFromDb(artifact: Escalation): Unit = {
    GenericEscalations.findOptionByName(artifact.name, None) match {
      case Some(escalation) ⇒
        deleteEscalationModel(escalation)
      case None ⇒ throwException(ArtifactNotFound(artifact.name, artifact.getClass))
    }
  }

  protected def createEscalationArtifact(art: Escalation): String = art match {
    case a: GenericEscalation        ⇒ createEscalationFromArtifact(DeploymentGenericEscalation(None, a)).name
    case a: ScaleInstancesEscalation ⇒ createEscalationFromArtifact(DeploymentGenericEscalation(None, a)).name
    case a: ScaleCpuEscalation       ⇒ createEscalationFromArtifact(DeploymentGenericEscalation(None, a)).name
    case a: ScaleMemoryEscalation    ⇒ createEscalationFromArtifact(DeploymentGenericEscalation(None, a)).name
    case a: ToAllEscalation          ⇒ createEscalationFromArtifact(DeploymentGenericEscalation(None, a)).name
    case a: ToOneEscalation          ⇒ createEscalationFromArtifact(DeploymentGenericEscalation(None, a)).name
  }

  protected def deleteEscalationModel(escalation: GenericEscalationModel): Unit = {
    for (param ← escalation.parameters) Parameters.deleteById(param.id.get)
    deleteChildEscalations(escalation.escalationReferences)
    GenericEscalations.deleteById(escalation.id.get)
  }

  private def deleteChildEscalations(escalations: List[EscalationReferenceModel]): Unit = {
    for (escalationRef ← escalations) {
      GenericEscalations.findOptionByName(escalationRef.name, escalationRef.deploymentId) match {
        case Some(escalationChild) if escalationChild.isAnonymous ⇒ deleteEscalationModel(escalationChild)
        case Some(escalationChild) ⇒ // Never delete children with a name
        case None ⇒ // Foreign key constraint should prevent this from happening
      }
      EscalationReferences.deleteById(escalationRef.id.get)
    }
  }

  protected def escalations2Artifacts(escalationReferences: List[EscalationReferenceModel]): List[Escalation] =
    escalationReferences.map(esc ⇒
      if (esc.isDefinedInline)
        findEscalationOptionArtifact(esc.name, esc.deploymentId) match {
        case Some(escalation: ScaleInstancesEscalation) ⇒ escalation
        case Some(escalation: ScaleCpuEscalation) ⇒ escalation
        case Some(escalation: ScaleMemoryEscalation) ⇒ escalation
        case Some(escalation: ToAllEscalation) ⇒ escalation
        case Some(escalation: ToOneEscalation) ⇒ escalation
        case Some(escalation: GenericEscalation) ⇒ escalation
        case _ ⇒ EscalationReference(esc.name)
      }
      else
        EscalationReference(esc.name)
    )

  protected def findEscalationOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    GenericEscalations.findOptionByName(name, defaultDeploymentId) match {
      case Some(e) ⇒
        e.escalationType match {
          case Constants.Escalation_Scale_Instances ⇒
            Some(ScaleInstancesEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), minimum = e.minimumInt.get, maximum = e.maximumInt.get, scaleBy = e.scaleByInt.get, targetCluster = e.targetCluster))
          case Constants.Escalation_Scale_Cpu ⇒
            Some(ScaleCpuEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), minimum = e.minimumDouble.get, maximum = e.maximumDouble.get, scaleBy = e.scaleByDouble.get, targetCluster = e.targetCluster))
          case Constants.Escalation_Scale_Memory ⇒
            Some(ScaleMemoryEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), minimum = e.minimumDouble.get, maximum = e.maximumDouble.get, scaleBy = e.scaleByDouble.get, targetCluster = e.targetCluster))
          case Constants.Escalation_To_all ⇒
            Some(ToAllEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), escalations = escalations2Artifacts(e.escalationReferences)))
          case Constants.Escalation_To_One ⇒
            Some(ToOneEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), escalations = escalations2Artifacts(e.escalationReferences)))
          case _ ⇒
            Some(GenericEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), `type` = e.escalationType, parameters = parametersToArtifact(e.parameters)))
        }
      case None ⇒ None
    }
  }

}
