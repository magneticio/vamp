package io.vamp.persistence

import _root_.io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.workflow.{ DefaultWorkflow, ScheduledWorkflow, WorkflowReference }
import io.vamp.persistence.notification.ArtifactNotFound

import scala.concurrent.Future
import scala.reflect._

trait ArtifactExpansion {
  this: NotificationProvider with ExecutionContextProvider ⇒

  protected def readExpanded(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]

  protected def expandReferences(artifact: Option[Artifact]): Future[Option[Artifact]] =
    if (artifact.isDefined) expandReferences(artifact.get).map(Option(_)) else Future.successful(None)

  protected def expandReferences(artifact: Artifact): Future[Artifact] = artifact match {
    case deployment: Deployment               ⇒ Future.successful(deployment)
    case blueprint: DefaultBlueprint          ⇒ expandClusters(blueprint.clusters).map(clusters ⇒ blueprint.copy(clusters = clusters))
    case breed: DefaultBreed                  ⇒ expandBreed(breed)
    case sla: Sla                             ⇒ expandSla(sla)
    case routing: DefaultRoute                ⇒ Future.sequence(routing.filters.map(expandIfReference[DefaultFilter, FilterReference])).map(filters ⇒ routing.copy(filters = filters))
    case escalation: GenericEscalation        ⇒ Future.successful(escalation)
    case filter: DefaultFilter                ⇒ Future.successful(filter)
    case scale: DefaultScale                  ⇒ Future.successful(scale)
    case scheduledWorkflow: ScheduledWorkflow ⇒ expandIfReference[DefaultWorkflow, WorkflowReference](scheduledWorkflow.workflow).map(workflow ⇒ scheduledWorkflow.copy(workflow = workflow))
    case _                                    ⇒ Future.successful(artifact)
  }

  private def expandBreed(breed: DefaultBreed): Future[DefaultBreed] = Future.sequence {
    breed.dependencies.map(item ⇒ expandIfReference[DefaultBreed, BreedReference](item._2).map(breed ⇒ (item._1, breed)))
  } map {
    case result ⇒ breed.copy(dependencies = result.toMap)
  }

  private def expandClusters(clusters: List[Cluster]): Future[List[Cluster]] = Future.sequence {
    clusters.map { cluster ⇒
      for {
        services ← expandServices(cluster.services)
        sla ← cluster.sla match {
          case Some(sla: SlaReference) ⇒ readExpanded(sla.name, classOf[GenericSla]) map {
            case Some(slaDefault: GenericSla) ⇒ Some(slaDefault.copy(escalations = sla.escalations))
            case _                            ⇒ throwException(ArtifactNotFound(sla.name, classOf[GenericSla]))
          }
          case Some(sla) ⇒ Future.successful(Some(sla))
          case None      ⇒ Future.successful(None)
        }
        routing ← expandGateways(cluster.routing)
      } yield cluster.copy(services = services, sla = sla, routing = routing)
    }
  }

  private def expandGateways(gateways: List[Gateway]): Future[List[Gateway]] = Future.sequence {
    gateways.map { gateway ⇒
      expandRoutes(gateway.routes).map { case routes ⇒ gateway.copy(routes = routes) }
    }
  }

  private def expandRoutes(routes: List[Route]): Future[List[Route]] = Future.sequence {
    routes.map { route ⇒ expandIfReference[DefaultRoute, RouteReference](route) }
  }

  private def expandServices(services: List[Service]): Future[List[Service]] = Future.sequence {
    services.map { service ⇒
      for {
        scale ← if (service.scale.isDefined) expandIfReference[DefaultScale, ScaleReference](service.scale.get).map(Option(_)) else Future.successful(None)
        breed ← expandIfReference[DefaultBreed, BreedReference](service.breed)
      } yield service.copy(scale = scale, breed = breed)
    }
  }

  private def expandSla(sla: Sla): Future[Sla] = Future.sequence {
    sla.escalations.map(expandIfReference[GenericEscalation, EscalationReference])
  } map {
    case escalations ⇒ sla match {
      case s: GenericSla                   ⇒ s.copy(escalations = escalations)
      case s: EscalationOnlySla            ⇒ s.copy(escalations = escalations)
      case s: ResponseTimeSlidingWindowSla ⇒ s.copy(escalations = escalations)
    }
  }

  private def expandIfReference[D <: Artifact: ClassTag, R <: Reference: ClassTag](artifact: Artifact): Future[D] = artifact match {
    case referencedArtifact if referencedArtifact.getClass == classTag[R].runtimeClass ⇒
      readExpanded(referencedArtifact.name, classTag[D].runtimeClass.asInstanceOf[Class[_ <: Artifact]]) map {
        case Some(defaultArtifact) if defaultArtifact.getClass == classTag[D].runtimeClass ⇒ defaultArtifact.asInstanceOf[D]
        case _ ⇒ throwException(ArtifactNotFound(referencedArtifact.name, classTag[D].runtimeClass))
      }
    case defaultArtifact if defaultArtifact.getClass == classTag[D].runtimeClass ⇒ Future.successful(defaultArtifact.asInstanceOf[D])
  }
}

trait ArtifactShrinkage {
  this: NotificationProvider ⇒

  protected def onlyReferences(artifact: Option[Artifact]): Option[Artifact] = artifact.flatMap(a ⇒ Some(onlyReferences(a)))

  protected def onlyReferences(artifact: Artifact): Artifact = artifact match {
    case blueprint: DefaultBlueprint ⇒ blueprint.copy(clusters = blueprint.clusters.map(cluster ⇒ cluster.copy(services = cluster.services.map(service ⇒ service.copy(breed = BreedReference(service.breed.name))))))
    case breed: DefaultBreed         ⇒ breed.copy(dependencies = breed.dependencies.map(item ⇒ item._1 -> BreedReference(item._2.name)))
    case _                           ⇒ artifact
  }
}
