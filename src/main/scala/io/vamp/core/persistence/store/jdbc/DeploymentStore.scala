package io.vamp.core.persistence.store.jdbc

import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider}
import io.vamp.core.persistence.slick.model._

import scala.slick.jdbc.JdbcBackend


trait DeploymentStore extends BlueprintStore with BreedStore with TraitNameParameterStore with ScaleStore with RoutingStore with SlaStore with PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._

  protected def updateDeployment(existing: DeploymentModel, artifact: Deployment): Unit = {
    deleteDeploymentClusters(existing.clusters, existing.id)
    deleteModelPorts(existing.endpoints)
    deleteModelTraitNameParameters(existing.parameters)
    createDeploymentClusters(artifact.clusters, existing.id)
    createPorts(artifact.endpoints, existing.id, Some(PortParentType.Deployment))
    createTraitNameParameters(artifact.parameters, existing.id, TraitParameterParentType.Deployment)
    Deployments.update(existing)
  }

  private def deleteDeploymentClusters(clusters: List[DeploymentClusterModel], deploymentId: Option[Int]): Unit = {
    for (cluster <- clusters) {
      for (route <- cluster.routes) {
        ClusterRoutes.deleteById(route.id.get)
      }
      for (service <- cluster.services) {
        for (dependency <- service.dependencies) {
          DeploymentServiceDependencies.deleteById(dependency.id.get)
        }
        for (server <- service.servers) {
          for (port <- server.ports) {
            ServerPorts.deleteById(port.id.get)
          }
          DeploymentServers.deleteById(server.id.get)
        }
        DeploymentServices.deleteById(service.id.get)
        service.breed match {
          case breedId => deleteDefaultBreedModel(DefaultBreeds.findById(breedId))
        }
        service.scale match {
          case Some(scaleId) => DefaultScales.deleteById(scaleId)
          case _ =>
        }
        service.routing match {
          case Some(routing) => deleteRoutingModel(DefaultRoutings.findById(routing))
          case _ =>
        }
      }
      DeploymentClusters.deleteById(cluster.id.get)
      cluster.slaReference match {
        case Some(slaRefId) =>
          SlaReferences.findOptionById(slaRefId) match {
            case Some(slaReference) =>
              GenericSlas.findOptionByName(slaReference.name, deploymentId) match {
                case Some(slaModel) => deleteSlaModel(slaModel)
                case _ =>
              }
              SlaReferences.deleteById(slaRefId)
            case None => // Foreign key constraint prevents this
          }
        case _ =>
      }
    }
  }

  private def createDeploymentClusters(clusters: List[DeploymentCluster], deploymentId: Option[Int]): Unit = {
    for (cluster <- clusters) {
      val slaRefId = createSla(cluster.sla, deploymentId)
      val clusterId = DeploymentClusters.add(DeploymentClusterModel(name = cluster.name, slaReference = slaRefId, deploymentId = deploymentId))
      for (route <- cluster.routes) {
        ClusterRoutes.add(ClusterRouteModel(portIn = route._1, portOut = route._2, clusterId = clusterId))
      }
      for (service <- cluster.services) {
        val breedId = createOrUpdateBreed(DeploymentDefaultBreed(deploymentId, service.breed)).id.get
        val scaleId = service.scale match {
          case Some(scale) => Some(DefaultScales.add(DeploymentDefaultScale(deploymentId, scale)))
          case _ => None
        }
        val routingId = service.routing match {
          case Some(routing) => createDefaultRoutingModelFromArtifact(DeploymentDefaultRouting(deploymentId, routing)).id
          case _ => None
        }
        val message = service.state match {
          case error: DeploymentService.Error =>
            Some(s"Problem in cluster ${cluster.name}, with a service containing breed ${DefaultBreeds.findById(breedId).name}.")
          case _ => None
        }

        val serviceId = DeploymentServices.add(
          DeploymentServiceModel(
            clusterId = clusterId,
            name = s"${cluster.name}~${java.util.UUID.randomUUID.toString}", // Dummy name
            deploymentId = deploymentId,
            breed = breedId,
            scale = scaleId,
            routing = routingId,
            deploymentState = service.state,
            deploymentTime = service.state.startedAt,
            message = message)
        )
        for (dep <- service.dependencies) DeploymentServiceDependencies.add(DeploymentServiceDependencyModel(name = dep._1, value = dep._2, serviceId = serviceId))
        for (server <- service.servers) {
          val serverId = DeploymentServers.add(DeploymentServerModel(serviceId = serviceId, name = server.name, host = server.host, deployed = server.deployed, deploymentId = deploymentId))
          for (port <- server.ports) ServerPorts.add(ServerPortModel(portIn = port._1, portOut = port._2, serverId = serverId))
        }
      }
    }
  }

  protected def findDeploymentOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    Deployments.findOptionByName(name) match {
      case Some(deployment) =>
        Some(Deployment(
          name = deployment.name,
          clusters = findDeploymentClusterArtifacts(deployment.clusters, deployment.id),
          endpoints = readPortsToArtifactList(deployment.endpoints),
          parameters = traitNameParametersToArtifactMap(deployment.parameters))
        )
      case _ => None
    }
  }

  private def findDeploymentClusterArtifacts(clusters: List[DeploymentClusterModel], deploymentId: Option[Int]): List[DeploymentCluster] =
    clusters.map(cluster =>
      DeploymentCluster(
        name = cluster.name,
        services = findDeploymentServiceArtifacts(cluster.services),
        routes = clusterRouteModels2Artifacts(cluster.routes),
        sla = findOptionSlaArtifactViaReferenceId(cluster.slaReference, deploymentId)
      )

    )

  private def clusterRouteModels2Artifacts(routes: List[ClusterRouteModel]): Map[Int, Int] =
    routes.map(route => route.portIn -> route.portOut).toMap

  private def findDeploymentServiceArtifacts(services: List[DeploymentServiceModel]): List[DeploymentService] =
    services.map(service =>

      DeploymentService(state = deploymentService2deploymentState(service),
        breed = defaultBreedModel2DefaultBreedArtifact(DefaultBreeds.findById(service.breed)),
        scale = service.scale match {
          case Some(scale) => Some(defaultScaleModel2Artifact(DefaultScales.findById(scale)))
          case _ => None
        },
        routing = service.routing match {
          case Some(routing) => Some(defaultRoutingModel2Artifact(DefaultRoutings.findById(routing)))
          case _ => None
        },
        servers = deploymentServerModels2Artifacts(service.servers),
        dependencies = deploymentServiceDependencies2Artifacts(service.dependencies)
      )
    )

  private def deploymentServerModels2Artifacts(servers: List[DeploymentServerModel]): List[DeploymentServer] =
    servers.map(server =>
      DeploymentServer(name = server.name, host = server.host, ports = serverPorts2Artifact(server.ports), deployed = server.deployed)
    )

  private def serverPorts2Artifact(ports: List[ServerPortModel]): Map[Int, Int] = ports.map(port => port.portIn -> port.portOut).toMap

  private def deploymentServiceDependencies2Artifacts(dependencies: List[DeploymentServiceDependencyModel]): Map[String, String] =
    dependencies.map(dep => dep.name -> dep.value).toMap

  protected def deleteDeploymentFromDb(artifact: Deployment): Unit = {
    Deployments.findOptionByName(artifact.name) match {
      case Some(deployment) => deleteDeploymentModel(deployment)
      case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
    }
  }

  private def deleteDeploymentModel(m: DeploymentModel): Unit = {
    deleteDeploymentClusters(m.clusters, m.id)
    deleteModelPorts(m.endpoints)
    deleteModelTraitNameParameters(m.parameters)
    Deployments.deleteById(m.id.get)
  }

  protected def createDeploymentArtifact(a: Deployment): String = {
    val deploymentId = Deployments.add(a)
    createDeploymentClusters(a.clusters, Some(deploymentId))
    createPorts(a.endpoints, Some(deploymentId), Some(PortParentType.Deployment))
    createTraitNameParameters(a.parameters, Some(deploymentId), TraitParameterParentType.Deployment)
    Deployments.findById(deploymentId).name
  }


}
