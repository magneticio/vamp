package io.vamp.persistence

import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.common.{ Artifact, Namespace, Reference }
import io.vamp.model.artifact._
import io.vamp.persistence.notification.ArtifactNotFound

import scala.concurrent.Future
import scala.reflect._

trait ArtifactExpansionSupport extends ArtifactSupport {
  this: ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  protected def readExpanded[T <: Artifact: ClassTag](name: String)(implicit namespace: Namespace): Future[Option[T]] = artifactForIfExists[T](name)

  protected def expandGateways(gateways: List[Gateway])(implicit namespace: Namespace): Future[List[Gateway]] = Future.sequence { gateways.map { gateway ⇒ expandGateway(gateway) } }

  protected def expandGateway(gateway: Gateway)(implicit namespace: Namespace): Future[Gateway] = {
    expandRoutes(gateway.routes).map { routes ⇒ gateway.copy(routes = routes) }
  }

  protected def expandRoutes(routes: List[Route])(implicit namespace: Namespace): Future[List[Route]] = Future.sequence {
    routes.map { route ⇒ expandIfReference[DefaultRoute, RouteReference](route).flatMap(expandRoute) }
  }

  protected def expandRoute(route: DefaultRoute)(implicit namespace: Namespace): Future[DefaultRoute] = route.condition match {
    case Some(condition) ⇒ expandIfReference[DefaultCondition, ConditionReference](condition).map(condition ⇒ route.copy(condition = Option(condition)))
    case _               ⇒ Future.successful(route)
  }

  protected def expandIfReference[D <: Artifact: ClassTag, R <: Reference: ClassTag](artifact: Artifact)(implicit namespace: Namespace): Future[D] = artifact match {
    case referencedArtifact if referencedArtifact.getClass == classTag[R].runtimeClass ⇒
      readExpanded[D](referencedArtifact.name) map {
        case Some(defaultArtifact) if defaultArtifact.getClass == classTag[D].runtimeClass ⇒ defaultArtifact.asInstanceOf[D]
        case _ ⇒ throwException(ArtifactNotFound(referencedArtifact.name, classTag[D].runtimeClass))
      }
    case defaultArtifact if defaultArtifact.getClass == classTag[D].runtimeClass ⇒ Future.successful(defaultArtifact.asInstanceOf[D])
  }
}

trait ArtifactExpansion {
  this: NotificationProvider with ExecutionContextProvider ⇒

  protected def get[T <: Artifact](name: String, `type`: Class[T]): Option[T]

  protected def expandReferences(artifact: Option[Artifact])(implicit namespace: Namespace): Option[Artifact] = {
    if (artifact.isDefined) Option(expandReferences(artifact.get)) else None
  }

  protected def expandReferences[T](artifact: T)(implicit namespace: Namespace): T = {
    val result = artifact match {
      case deployment: Deployment        ⇒ deployment
      case blueprint: DefaultBlueprint   ⇒ blueprint.copy(clusters = expandClusters(blueprint.clusters))
      case breed: DefaultBreed           ⇒ expandBreed(breed)
      case sla: Sla                      ⇒ expandSla(sla)
      case route: DefaultRoute           ⇒ expandRoute(route)
      case escalation: GenericEscalation ⇒ escalation
      case condition: DefaultCondition   ⇒ condition
      case scale: DefaultScale           ⇒ scale
      case workflow: Workflow            ⇒ expandWorkflow(workflow)
      case _                             ⇒ artifact
    }
    result.asInstanceOf[T]
  }

  private def expandBreed(breed: DefaultBreed)(implicit namespace: Namespace): DefaultBreed = {
    val dependencies = breed.dependencies.mapValues(item ⇒ expandIfReference[DefaultBreed, BreedReference](item))
    breed.copy(dependencies = dependencies)
  }

  private def expandClusters(clusters: List[Cluster])(implicit namespace: Namespace): List[Cluster] = {
    clusters.map { cluster ⇒
      val services = expandServices(cluster.services)
      val sla = {
        cluster.sla match {
          case Some(sla: SlaReference) ⇒ readExpanded[GenericSla](sla.name) match {
            case Some(slaDefault: GenericSla) ⇒ Some(slaDefault.copy(escalations = sla.escalations))
            case _                            ⇒ throwException(ArtifactNotFound(sla.name, classOf[GenericSla]))
          }
          case Some(s) ⇒ Some(s)
          case None    ⇒ None
        }
      }
      val routing = expandGateways(cluster.gateways)
      cluster.copy(services = services, sla = sla, gateways = routing)
    }
  }

  private def expandGateways(gateways: List[Gateway])(implicit namespace: Namespace): List[Gateway] = {
    gateways.map { gateway ⇒ expandGateway(gateway) }
  }

  private def expandGateway(gateway: Gateway)(implicit namespace: Namespace): Gateway = {
    gateway.copy(routes = expandRoutes(gateway.routes))
  }

  private def expandRoutes(routes: List[Route])(implicit namespace: Namespace): List[Route] = routes.map { route ⇒
    expandRoute(expandIfReference[DefaultRoute, RouteReference](route))
  }

  private def expandServices(services: List[Service])(implicit namespace: Namespace): List[Service] = services.map { service ⇒
    val scale = service.scale.map(expandIfReference[DefaultScale, ScaleReference])
    val breed = expandIfReference[DefaultBreed, BreedReference](service.breed)
    service.copy(scale = scale, breed = breed)
  }

  private def expandSla(sla: Sla)(implicit namespace: Namespace): Sla = {
    val escalations = sla.escalations.map(expandIfReference[GenericEscalation, EscalationReference])
    sla match {
      case s: GenericSla                   ⇒ s.copy(escalations = escalations)
      case s: EscalationOnlySla            ⇒ s.copy(escalations = escalations)
      case s: ResponseTimeSlidingWindowSla ⇒ s.copy(escalations = escalations)
    }
  }

  private def expandRoute(route: DefaultRoute)(implicit namespace: Namespace): DefaultRoute = route.condition match {
    case Some(condition) ⇒ route.copy(condition = Option(expandIfReference[DefaultCondition, ConditionReference](condition)))
    case _               ⇒ route
  }

  private def expandWorkflow(workflow: Workflow)(implicit namespace: Namespace): Workflow = {
    val breed = expandIfReference[DefaultBreed, BreedReference](workflow.breed)
    val scale = workflow.scale.map(expandIfReference[DefaultScale, ScaleReference])
    workflow.copy(scale = scale, breed = breed)
  }

  private def expandIfReference[D <: Artifact: ClassTag, R <: Reference: ClassTag](artifact: Artifact)(implicit namespace: Namespace): D = artifact match {
    case referencedArtifact if referencedArtifact.getClass == classTag[R].runtimeClass ⇒
      readExpanded[D](referencedArtifact.name) match {
        case Some(defaultArtifact) if defaultArtifact.getClass == classTag[D].runtimeClass ⇒ defaultArtifact.asInstanceOf[D]
        case _ ⇒ throwException(ArtifactNotFound(referencedArtifact.name, classTag[D].runtimeClass))
      }
    case defaultArtifact if defaultArtifact.getClass == classTag[D].runtimeClass ⇒ defaultArtifact.asInstanceOf[D]
  }

  private def readExpanded[T <: Artifact: ClassTag](name: String)(implicit namespace: Namespace): Option[T] = {
    get(name, classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]).asInstanceOf[Option[T]]
  }
}

trait ArtifactShrinkage {
  this: NotificationProvider ⇒

  protected def onlyReferences(artifact: Option[Artifact]): Option[Artifact] = artifact.flatMap(a ⇒ Some(onlyReferences(a)))

  protected def onlyReferences(artifact: Artifact): Artifact = artifact match {
    case blueprint: DefaultBlueprint ⇒ blueprint.copy(clusters = blueprint.clusters.map(cluster ⇒ cluster.copy(services = cluster.services.map(service ⇒ service.copy(breed = BreedReference(service.breed.name))))))
    case breed: DefaultBreed         ⇒ breed.copy(dependencies = breed.dependencies.map(item ⇒ item._1 → BreedReference(item._2.name)))
    case _                           ⇒ artifact
  }
}
