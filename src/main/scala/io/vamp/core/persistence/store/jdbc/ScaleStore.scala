package io.vamp.core.persistence.store.jdbc

import io.vamp.core.model.artifact.{Artifact, DefaultScale, Scale, ScaleReference}
import io.vamp.core.persistence.slick.model.{DefaultScaleModel, DeploymentDefaultScale, ScaleReferenceModel}

import scala.slick.jdbc.JdbcBackend


trait ScaleStore {
  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._

  protected def createScaleReference(artifact: Option[Scale], deploymentId: Option[Int]): Option[Int] = artifact match {
    case Some(scale: DefaultScale) =>
      DefaultScales.findOptionByName(scale.name, deploymentId) match {
        case Some(existing) => updateScale(existing, scale)
          Some(ScaleReferences.add(ScaleReferenceModel(deploymentId = deploymentId, name = existing.name, isDefinedInline = true)))
        case None =>
          val scaleName = createDefaultScaleModelFromArtifact(DeploymentDefaultScale(deploymentId, scale)).name
          Some(ScaleReferences.add(ScaleReferenceModel(deploymentId = deploymentId, name = scaleName, isDefinedInline = true)))
      }
    case Some(scale: ScaleReference) =>
      Some(ScaleReferences.add(ScaleReferenceModel(deploymentId = deploymentId, name = scale.name, isDefinedInline = false)))
    case _ => None
  }

  protected def updateScale(existing: DefaultScaleModel, a: DefaultScale): Unit =
    existing.copy(cpu = a.cpu, memory = a.memory, instances = a.instances).update

  protected def createDefaultScaleModelFromArtifact(artifact: DeploymentDefaultScale): DefaultScaleModel =
    DefaultScales.findById(DefaultScales.add(artifact))

  protected def findOptionScaleArtifactViaReferenceName(artifactId: Option[Int], deploymentId: Option[Int]): Option[Scale] = artifactId match {
    case Some(scaleRefId) =>
      ScaleReferences.findOptionById(scaleRefId) match {
        case Some(ref: ScaleReferenceModel) if ref.isDefinedInline =>
          DefaultScales.findOptionByName(ref.name, deploymentId) match {
            case Some(defaultScale) => Some(defaultScale)
            case None => Some(ScaleReference(name = ref.name)) // Not found, return a reference instead
          }
        case Some(ref) => Some(ScaleReference(name = ref.name))
        case None => None
      }
    case None => None
  }

  protected def findScaleOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    DefaultScales.findOptionByName(name, defaultDeploymentId).map(a => a)
  }

  protected def deleteScaleFromDb(artifact: DefaultScale): Unit = {
    DefaultScales.deleteByName(artifact.name, None)
  }

  protected def createScaleArtifact(art: Scale): String =
    art match {
      case a: DefaultScale => createDefaultScaleModelFromArtifact(DeploymentDefaultScale(None, a)).name
    }


}