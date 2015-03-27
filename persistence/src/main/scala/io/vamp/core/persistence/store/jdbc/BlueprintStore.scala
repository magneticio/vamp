package io.vamp.core.persistence.store.jdbc

import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider}
import io.vamp.core.persistence.slick.model._
import io.vamp.core.persistence.slick.util.VampPersistenceUtil

import scala.slick.jdbc.JdbcBackend

trait BlueprintStore extends BreedStore with TraitNameParameterStore with ScaleStore with RoutingStore with SlaStore with PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._

  protected def updateBlueprint(existing: DefaultBlueprintModel, artifact: DefaultBlueprint): Unit = {
    deleteBlueprintClusters(existing.clusters)
    createBlueprintClusters(artifact.clusters, existing.id.get, existing.deploymentId)
    deleteModelPorts(existing.endpoints)
    createPorts(artifact.endpoints, existing.id, parentType = Some(PortParentType.BlueprintEndpoint))
    deleteModelTraitNameParameters(existing.parameters)
    createTraitNameParameters(artifact.parameters, existing.id, TraitParameterParentType.Blueprint)
    existing.update
  }

  private def deleteBlueprintClusters(clusters: List[ClusterModel]): Unit = {
    for (cluster <- clusters) {
      for (service <- cluster.services) {
        Services.deleteById(service.id.get)
        BreedReferences.findOptionByName(service.breedReferenceName, service.deploymentId) match {
          case Some(breedRef) =>
            if (breedRef.isDefinedInline)
              DefaultBreeds.findOptionByName(breedRef.name, service.deploymentId) match {
                case Some(breed) if breed.isAnonymous => deleteDefaultBreedModel(breed)
                case Some(breed) =>
                case None => // Should not happen (log it as not critical)
              }
            BreedReferences.deleteById(breedRef.id.get)
          case None => /// Should not happen (log it as not critical)
        }
        service.scaleReference match {
          case Some(scaleRefId) =>
            ScaleReferences.findOptionById(scaleRefId) match {
              case Some(scaleRef) if scaleRef.isDefinedInline =>
                DefaultScales.findOptionByName(scaleRef.name, service.deploymentId) match {
                  case Some(scale) if scale.isAnonymous => DefaultScales.deleteById(scale.id.get)
                  case Some(scale) =>
                  case None => // Should not happen (log it as not critical)
                }
                ScaleReferences.deleteById(scaleRefId)
              case Some(scaleRef) =>
                ScaleReferences.deleteById(scaleRefId)
              case None => // Should not happen (log it as not critical)
            }
          case None => // Nothing to delete
        }
        service.routingReference match {
          case Some(routingId) =>
            RoutingReferences.findOptionById(routingId) match {
              case Some(routingRef) if routingRef.isDefinedInline =>
                DefaultRoutings.findOptionByName(routingRef.name, service.deploymentId) match {
                  case Some(routing) if routing.isAnonymous => deleteRoutingModel(routing)
                  case Some(routing) =>
                  case None => // Should not happen (log it as not critical)
                }
                RoutingReferences.deleteById(routingRef.id.get)
              case Some(routingRef) =>
                RoutingReferences.deleteById(routingRef.id.get)
              case None => // Should not happen (log it as not critical)
            }
          case None => // Nothing to delete
        }
      }
      Clusters.deleteById(cluster.id.get)

      cluster.slaReference match {
        case Some(slaRef) =>
          GenericSlas.findOptionByName(slaRef, cluster.deploymentId) match {
            case Some(sla) if sla.isAnonymous => deleteSlaModel(sla)
            case Some(sla) =>
            case None => // Should not happen
          }
          SlaReferences.findOptionByName(slaRef, cluster.deploymentId) match {
            case Some(slaReference) =>
              for (escalationReference <- slaReference.escalationReferences) {
                EscalationReferences.deleteById(escalationReference.id.get)
              }
            case None =>
          }
          SlaReferences.deleteByName(slaRef, cluster.deploymentId)

        case None => // Should not happen
      }
    }
  }

  protected def findBlueprintOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    DefaultBlueprints.findOptionByName(name, defaultDeploymentId) match {
      case Some(b) => Some(
        DefaultBlueprint(
          name = VampPersistenceUtil.restoreToAnonymous(b.name, b.isAnonymous),
          clusters = findBlueprintClusterArtifacts(b.clusters, defaultDeploymentId),
          endpoints = readPortsToArtifactList(b.endpoints),
          parameters = traitNameParametersToArtifactMap(b.parameters)
        )
      )
      case None => None
    }
  }

  private def findBlueprintClusterArtifacts(clusters: List[ClusterModel], deploymentId: Option[Int]): List[Cluster] =
    clusters.map(cluster => Cluster(
      name = cluster.name,
      services = findServicesArtifacts(cluster.services, deploymentId),
      sla = findOptionSlaArtifactViaReferenceName(cluster.slaReference, deploymentId))
    )

  private def findServicesArtifacts(services: List[ServiceModel], deploymentId: Option[Int]): List[Service] = services.map(service =>
    Service(
      breed = findBreedArtifactViaReference(service.breedReferenceName, deploymentId),
      scale = findOptionScaleArtifactViaReferenceName(service.scaleReference, deploymentId),
      routing = findOptionRoutingArtifactViaReference(service.routingReference, deploymentId)
    )
  )

  protected def deleteBlueprintFromDb(artifact: DefaultBlueprint): Unit = {
    DefaultBlueprints.findOptionByName(artifact.name, None) match {
      case Some(blueprint) => deleteBlueprintModel(blueprint)
      case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
    }
  }

  private def deleteBlueprintModel(blueprint: DefaultBlueprintModel): Unit = {
    deleteBlueprintClusters(blueprint.clusters)
    deleteModelTraitNameParameters(blueprint.parameters)
    deleteModelPorts(blueprint.endpoints)
    DefaultBlueprints.deleteById(blueprint.id.get)
  }

  protected def createBlueprintArtifact(art: Blueprint): String = art match {
    case a: DefaultBlueprint =>
      val deploymentId: Option[Int] = None
      val blueprintId = DefaultBlueprints.add(DeploymentDefaultBlueprint(deploymentId, a))
      createBlueprintClusters(a.clusters, blueprintId, deploymentId)
      createPorts(ports = a.endpoints, parentId = Some(blueprintId), parentType = Some(PortParentType.BlueprintEndpoint))
      createTraitNameParameters(a.parameters, Some(blueprintId), TraitParameterParentType.Blueprint)
      DefaultBlueprints.findById(blueprintId).name
  }

  private def createBlueprintClusters(clusters: List[Cluster], blueprintId: Int, deploymentId: Option[Int]): Unit = {
    for (cluster <- clusters) {
      val slaRefName: Option[String] = createSla(cluster.sla, deploymentId)
      val clusterId = Clusters.add(ClusterModel(deploymentId = deploymentId, name = cluster.name, blueprintId = blueprintId, slaReference = slaRefName))
      createServices(cluster.services, clusterId, deploymentId)
    }
  }

  private def createServices(services: List[Service], clusterId: Int, deploymentId: Option[Int]): Unit = {
    services.map(service =>
      Services.add(ServiceModel(
        deploymentId = deploymentId,
        clusterId = clusterId,
        breedReferenceName = createBreedReference(service.breed, deploymentId),
        routingReference = createRoutingReference(service.routing, deploymentId),
        scaleReference = createScaleReference(service.scale, deploymentId))
      )
    )
  }


}
