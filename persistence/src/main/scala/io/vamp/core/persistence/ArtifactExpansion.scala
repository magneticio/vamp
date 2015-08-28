package io.vamp.core.persistence

import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact._
import io.vamp.core.model.workflow.{DefaultWorkflow, ScheduledWorkflow, WorkflowReference}
import io.vamp.core.persistence.notification.ArtifactNotFound

import scala.reflect._

trait ArtifactExpansion {
  this: NotificationProvider =>

  protected def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact]

  protected def expandReferences(artifact: Option[Artifact]): Option[Artifact] = artifact.flatMap(a => Some(expandReferences(a)))

  protected def expandReferences(artifact: Artifact): Artifact = artifact match {
    case deployment: Deployment => deployment // Deployments are already fully expanded
    case blueprint: DefaultBlueprint => blueprint.copy(clusters = expandClusters(blueprint.clusters))
    case breed: DefaultBreed => breed.copy(dependencies = breed.dependencies.map(item => item._1 -> expandIfReference[DefaultBreed, BreedReference](item._2)))
    case sla: GenericSla => sla.copy(escalations = sla.escalations.map(expandIfReference[GenericEscalation, EscalationReference]))
    case sla: EscalationOnlySla => sla.copy(escalations = sla.escalations.map(expandIfReference[GenericEscalation, EscalationReference]))
    case sla: ResponseTimeSlidingWindowSla => sla.copy(escalations = sla.escalations.map(expandIfReference[GenericEscalation, EscalationReference]))
    case routing: DefaultRouting => routing.copy(filters = routing.filters.map(expandIfReference[DefaultFilter, FilterReference]))
    case escalation: GenericEscalation => escalation
    case filter: DefaultFilter => filter
    case scale: DefaultScale => scale
    case scheduledWorkflow: ScheduledWorkflow => scheduledWorkflow.copy(workflow = expandIfReference[DefaultWorkflow, WorkflowReference](scheduledWorkflow.workflow))
    case _ => artifact
  }

  private def expandClusters(clusters: List[Cluster]): List[Cluster] = clusters.map { cluster =>
    cluster.copy(services = expandServices(cluster.services),
      sla = cluster.sla match {
        case Some(sla: GenericSla) => Some(sla)
        case Some(sla: EscalationOnlySla) => Some(sla)
        case Some(sla: ResponseTimeSlidingWindowSla) => Some(sla)
        case Some(sla: SlaReference) => read(sla.name, classOf[GenericSla]) match {
          case Some(slaDefault: GenericSla) => Some(slaDefault.copy(escalations = sla.escalations))
          case _ => throwException(ArtifactNotFound(sla.name, classOf[GenericSla]))
        }
        case _ => None
      }
    )
  }

  private def expandServices(services: List[Service]): List[Service] = services.map { service =>
    service.copy(
      routing = service.routing.flatMap(routing => Some(expandIfReference[DefaultRouting, RoutingReference](routing))),
      scale = service.scale.flatMap(scale => Some(expandIfReference[DefaultScale, ScaleReference](scale))),
      breed = expandIfReference[DefaultBreed, BreedReference](service.breed)
    )
  }

  private def expandIfReference[D <: Artifact : ClassTag, R <: Reference : ClassTag](artifact: Artifact): D = artifact match {
    case referencedArtifact if referencedArtifact.getClass == classTag[R].runtimeClass =>
      read(referencedArtifact.name, classTag[D].runtimeClass.asInstanceOf[Class[_ <: Artifact]]) match {
        case Some(defaultArtifact) if defaultArtifact.getClass == classTag[D].runtimeClass => defaultArtifact.asInstanceOf[D]
        case _ => throwException(ArtifactNotFound(referencedArtifact.name, classTag[D].runtimeClass))
      }
    case defaultArtifact if defaultArtifact.getClass == classTag[D].runtimeClass => defaultArtifact.asInstanceOf[D]
  }
}

trait ArtifactShrinkage {
  this: NotificationProvider =>

  protected def onlyReferences(artifact: Option[Artifact]): Option[Artifact] = artifact.flatMap(a => Some(onlyReferences(a)))

  protected def onlyReferences(artifact: Artifact): Artifact = artifact match {
    case blueprint: DefaultBlueprint => blueprint.copy(clusters = blueprint.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(breed = BreedReference(service.breed.name))))))
    case breed: DefaultBreed => breed.copy(dependencies = breed.dependencies.map(item => item._1 -> BreedReference(item._2.name)))
    case _ => artifact
  }
}