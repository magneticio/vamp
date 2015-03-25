package io.vamp.core.persistence.store

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.core.model.artifact.Trait.Name
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider, PersistenceOperationFailure, UnsupportedPersistenceRequest}
import io.vamp.core.persistence.slick.components.Components
import io.vamp.core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.vamp.core.persistence.slick.model.PortParentType.PortParentType
import io.vamp.core.persistence.slick.model.TraitParameterParentType.TraitParameterParentType
import io.vamp.core.persistence.slick.model._
import io.vamp.core.persistence.slick.util.VampPersistenceUtil

import scala.slick.jdbc.JdbcBackend._


/**
 * JDBC storage of artifacts
 */
trait JdbcStoreProvider extends StoreProvider with PersistenceNotificationProvider {
  this: ExecutionContextProvider =>


  val db: Database = Database.forConfig("persistence.jdbcProvider")
  implicit val sess = db.createSession()

  override val store: Store = new JdbcStore()


  private class JdbcStore extends Store {

    import io.vamp.core.persistence.slick.components.Components.instance._
    import io.vamp.core.persistence.slick.model.Implicits._

    Components.instance.upgradeSchema

    def create(artifact: Artifact, ignoreIfExists: Boolean): Artifact = {
      read(artifact.name, artifact.getClass) match {
        case None => addArtifact(artifact)
        case Some(storedArtifact) if !ignoreIfExists => update(artifact, create = false)
        case Some(storedArtifact) if ignoreIfExists => storedArtifact
      }
    }

    def read(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] = {
      readToArtifact(name, ofType)
    }

    def update(artifact: Artifact, create: Boolean): Artifact = {
      read(artifact.name, artifact.getClass) match {
        case None =>
          if (create) this.addArtifact(artifact)
          else throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
        case Some(existingArtifact) => updateArtifact(artifact)
      }
    }

    def delete(name: String, ofType: Class[_ <: Artifact]): Artifact = {
      readToArtifact(name, ofType) match {
        case Some(artifact) =>
          deleteArtifact(artifact)
          artifact
        case None =>
          throw exception(ArtifactNotFound(name, ofType))
      }
    }

    def all(ofType: Class[_ <: Artifact]): List[_ <: Artifact] = {
      ofType match {
        case _ if ofType == classOf[Deployment] => Deployments.fetchAll.map(a => read(a.name, ofType).get)
        case _ if ofType == classOf[DefaultBlueprint] => DefaultBlueprints.fetchAll.map(a => read(a.name, ofType).get)
        case _ if ofType == classOf[DefaultEscalation] => DefaultEscalations.fetchAll.map(a => read(a.name, ofType).get)
        case _ if ofType == classOf[DefaultFilter] => DefaultFilters.fetchAll.map(a => read(a.name, ofType).get)
        case _ if ofType == classOf[DefaultRouting] => DefaultRoutings.fetchAll.map(a => read(a.name, ofType).get)
        case _ if ofType == classOf[DefaultScale] => DefaultScales.fetchAll.map(a => read(a.name, ofType).get)
        case _ if ofType == classOf[DefaultSla] => DefaultSlas.fetchAll.map(a => read(a.name, ofType).get)
        case _ if ofType == classOf[DefaultBreed] => DefaultBreeds.fetchAll.map(a => read(a.name, ofType).get)
        case _ => throw exception(UnsupportedPersistenceRequest(ofType))
      }
    }

    private def defaultRoutingModel2Artifact(r: DefaultRoutingModel): DefaultRouting = {
      val filters: List[Filter] = r.filterReferences.map(filter =>
        if (filter.isDefinedInline)
          DefaultFilters.findOptionByName(filter.name, filter.deploymentId) match {
            case Some(defaultFilter: DefaultFilterModel) => defaultFilterModel2Artifact(defaultFilter)
            case _ => FilterReference(filter.name)
          }
        else
          FilterReference(filter.name)
      )
      DefaultRouting(name = VampPersistenceUtil.restoreToAnonymous(r.name, r.isAnonymous), weight = r.weight, filters = filters)
    }

    private def breedDependencies2Artifact(dependencies: List[DependencyModel]): Map[String, Breed] = (for {
      d <- dependencies
      breedRef = if (d.isDefinedInline) {
        readToArtifact(d.breedName, classOf[DefaultBreed]) match {
          case Some(childBreed: DefaultBreed) => childBreed
          case Some(childBreed: BreedReference) => childBreed
          case _ => BreedReference(d.breedName) //Not found, return a reference instead
        }
      } else {
        BreedReference(d.breedName)
      }
    } yield d.name -> breedRef).toMap

    private def defaultBreedModel2DefaultBreedArtifact(b: DefaultBreedModel): DefaultBreed =
      DefaultBreed(name = VampPersistenceUtil.restoreToAnonymous(b.name, b.isAnonymous),
        deployable = Deployable(b.deployable),
        ports = readPortsToArtifactList(b.ports),
        environmentVariables = b.environmentVariables.map(e => environmentVariableModel2Artifact(e)),
        dependencies = breedDependencies2Artifact(b.dependencies))


    private def deleteExistingParameters(parameters: List[ParameterModel]): Unit =
      for (param <- parameters) Parameters.deleteById(param.id.get)

    private def deleteFilterReferences(filterReferences: List[FilterReferenceModel]): Unit = {
      for (filter <- filterReferences) {
        if (filter.isDefinedInline) {
          DefaultFilters.findOptionByName(filter.name, filter.deploymentId) match {
            case Some(storedFilter) if storedFilter.isAnonymous => DefaultFilters.deleteByName(filter.name, filter.deploymentId)
            case _ =>
          }
        }
        FilterReferences.deleteByName(filter.name, filter.deploymentId)
      }
    }

    private def updateSla(existing: DefaultSlaModel, a: DefaultSla): Unit = {
      deleteSlaModelChildObjects(existing)
      createEscalationReferences(a.escalations, existing.id, None, existing.deploymentId)
      createParameters(a.parameters, existing.id.get, ParameterParentType.Sla)
      existing.copy(slaType = a.`type`).update
    }

    private def updateBreed(existing: DefaultBreedModel, a: DefaultBreed): Unit = {
      for (d <- existing.dependencies) {
        if (d.isDefinedInline) {
          DefaultBreeds.findOptionByName(d.breedName, d.deploymentId) match {
            case Some(breed) => if (breed.isAnonymous) deleteDefaultBreedModel(breed)
            case _ =>
          }
        }
        Dependencies.deleteById(d.id.get)
      }
      deleteModelPorts(existing.ports)
      for (e <- existing.environmentVariables) {
        EnvironmentVariables.deleteById(e.id.get)
      }
      createBreedChildren(existing, DeploymentDefaultBreed(existing.deploymentId, a))
      existing.copy(deployable = a.deployable.name).update
    }

    private def updateRouting(existing: DefaultRoutingModel, artifact: DefaultRouting): Unit = {
      deleteFilterReferences(existing.filterReferences)
      createFilterReferences(DeploymentDefaultRouting(existing.deploymentId, artifact), existing.id.get)
      existing.copy(weight = artifact.weight).update
    }

    private def updateBlueprint(existing: DefaultBlueprintModel, artifact: DefaultBlueprint): Unit = {
      deleteBlueprintClusters(existing.clusters)
      createBlueprintClusters(artifact.clusters, existing.id.get, existing.deploymentId)
      deleteModelPorts(existing.endpoints)
      createPorts(artifact.endpoints, existing.id, parentType = Some(PortParentType.BlueprintEndpoint))
      deleteModelTraitNameParameters(existing.parameters)
      createTraitNameParameters(artifact.parameters, existing.id, TraitParameterParentType.Blueprint)
      existing.update
    }

    private def deleteModelPorts(ports: List[PortModel]): Unit =
      for (p <- ports) Ports.deleteById(p.id.get)

    private def deleteModelTraitNameParameters(params: List[TraitNameParameterModel]): Unit =
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


    private def updateScale(existing: DefaultScaleModel, a: DefaultScale): Unit =
      existing.copy(cpu = a.cpu, memory = a.memory, instances = a.instances).update

    private def updateDeployment(existing: DeploymentModel, artifact: Deployment): Unit = {
      deleteDeploymentClusters(existing.clusters, existing.id)
      deleteModelPorts(existing.endpoints)
      deleteModelTraitNameParameters(existing.parameters)
      createDeploymentClusters(artifact.clusters, existing.id)
      createPorts(artifact.endpoints, existing.id, Some(PortParentType.Deployment))
      createTraitNameParameters(artifact.parameters, existing.id, TraitParameterParentType.Deployment)
      Deployments.update(existing)
    }

    private def updateArtifact(artifact: Artifact): Artifact = {
      val deploymentId: Option[Int] = None
      artifact match {

        case a: Deployment =>
          updateDeployment(Deployments.findByName(a.name), a)

        case a: DefaultBlueprint =>
          updateBlueprint(DefaultBlueprints.findByName(a.name, deploymentId), a)

        case a: DefaultEscalation =>
          updateEscalation(DeploymentDefaultEscalation(deploymentId, a))

        case a: DefaultFilter =>
          DefaultFilters.findByName(a.name, deploymentId).copy(condition = a.condition).update

        case a: DefaultRouting =>
          updateRouting(DefaultRoutings.findByName(a.name, deploymentId), a)

        case a: DefaultScale =>
          updateScale(DefaultScales.findByName(a.name, deploymentId), a)

        case a: DefaultSla =>
          updateSla(DefaultSlas.findByName(a.name, deploymentId), a)

        case a: DefaultBreed =>
          updateBreed(DefaultBreeds.findByName(a.name, deploymentId), a)

        case _ => throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
      read(artifact.name, artifact.getClass).get
    }

    private def updateEscalation(a: DeploymentDefaultEscalation): Unit = {
      val existing = DefaultEscalations.findByName(a.artifact.name, a.deploymentId)
      deleteExistingParameters(existing.parameters)
      createParameters(a.artifact.parameters, existing.id.get, ParameterParentType.Escalation)
      existing.copy(escalationType = a.artifact.`type`).update
    }

    private def parametersToArtifact(e: List[ParameterModel]): Map[String, Any] = {
      (for {param <- e
            value = param.parameterType match {
              case ParameterType.Int => param.intValue
              case ParameterType.Double => param.doubleValue
              case ParameterType.String => param.stringValue.get
            }
      } yield param.name -> value).toMap
    }

    private def findOptionRoutingArtifactViaReference(referenceId: Option[Int], deploymentId: Option[Int]): Option[Routing] = referenceId match {
      case Some(routingRefId) =>
        RoutingReferences.findOptionById(routingRefId) match {
          case Some(ref: RoutingReferenceModel) if ref.isDefinedInline =>
            DefaultRoutings.findOptionByName(ref.name, deploymentId) match {
              case Some(defaultRouting) => Some(defaultRoutingModel2Artifact(defaultRouting))
              case None => Some(RoutingReference(name = ref.name)) // Not found, return a reference instead
            }
          case Some(ref) => Some(RoutingReference(name = ref.name))
          case None => None
        }
      case None => None
    }

    private def findBreedArtifactViaReference(artifactName: String, deploymentId: Option[Int]): Breed =
      BreedReferences.findOptionByName(artifactName, deploymentId) match {
        case Some(breedRef) if breedRef.isDefinedInline =>
          DefaultBreeds.findOptionByName(breedRef.name, deploymentId) match {
            case Some(b) => defaultBreedModel2DefaultBreedArtifact(b)
            case None => BreedReference(name = artifactName) //Not found, return a reference instead
          }
        case _ => BreedReference(name = artifactName)
      }

    private def findOptionSlaArtifactViaReferenceName(artifactName: Option[String], deploymentId: Option[Int]): Option[Sla] = artifactName match {
      case Some(slaName) =>
        SlaReferences.findOptionByName(slaName, deploymentId) match {
          case Some(slaReference) if slaReference.isDefinedInline =>
            readToArtifact(slaReference.name, classOf[DefaultSla]) match {
              case Some(slaArtifact: DefaultSla) => Some(slaArtifact)
              case Some(slaArtifact: SlaReference) => Some(slaArtifact)
              case _ => None
            }
          case Some(slaReference) =>
            Some(SlaReference(name = slaReference.name, escalations = escalations2Artifacts(slaReference.escalationReferences)))
          case None => None
        }
      case None => None
    }

    private def findOptionScaleArtifactViaReferenceName(artifactId: Option[Int], deploymentId: Option[Int]): Option[Scale] = artifactId match {
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

    private def findServicesArtifacts(services: List[ServiceModel], deploymentId: Option[Int]): List[Service] = services.map(service =>
      Service(
        breed = findBreedArtifactViaReference(service.breedReferenceName, deploymentId),
        scale = findOptionScaleArtifactViaReferenceName(service.scaleReference, deploymentId),
        routing = findOptionRoutingArtifactViaReference(service.routingReference, deploymentId)
      )
    )

    private def findBlueprintClusterArtifacts(clusters: List[ClusterModel], deploymentId: Option[Int]): List[Cluster] =
      clusters.map(cluster => Cluster(
        name = cluster.name,
        services = findServicesArtifacts(cluster.services, deploymentId),
        sla = findOptionSlaArtifactViaReferenceName(cluster.slaReference, deploymentId))
      )


    private def serverPorts2Artifact(ports: List[ServerPortModel]): Map[Int, Int] = ports.map(port => port.portIn -> port.portOut).toMap


    private def deploymentServerModels2Artifacts(servers: List[DeploymentServerModel]): List[DeploymentServer] =
      servers.map(server =>
        DeploymentServer(name = server.name, host = server.host, ports = serverPorts2Artifact(server.ports), deployed = server.deployed)
      )

    private def deploymentServiceDependencies2Artifacts(dependencies: List[DeploymentServiceDependencyModel]): Map[String, String] =
      dependencies.map(dep => dep.name -> dep.value).toMap


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

    private def clusterRouteModels2Artifacts(routes: List[ClusterRouteModel]): Map[Int, Int] =
      routes.map(route => route.portIn -> route.portOut).toMap

    private def findDeploymentClusterArtifacts(clusters: List[DeploymentClusterModel], deploymentId: Option[Int]): List[DeploymentCluster] =
      clusters.map(cluster =>
        DeploymentCluster(
          name = cluster.name,
          services = findDeploymentServiceArtifacts(cluster.services),
          routes = clusterRouteModels2Artifacts(cluster.routes),
          sla = findOptionSlaArtifactViaReferenceName(cluster.slaReference, deploymentId)
        )

      )

    private def readPortsToArtifactList(ports: List[PortModel]): List[Port] = ports.map(p => portModel2Port(p))

    private def readToArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] = {
      val defaultDeploymentId: Option[Int] = None
      ofType match {

        case _ if ofType == classOf[Deployment] =>
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

        case _ if ofType == classOf[DefaultBlueprint] =>
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

        case _ if ofType == classOf[DefaultEscalation] =>
          DefaultEscalations.findOptionByName(name, defaultDeploymentId) match {
            case Some(e) =>
              Some(DefaultEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), `type` = e.escalationType, parameters = parametersToArtifact(e.parameters)))
            case None => None
          }

        case _ if ofType == classOf[DefaultFilter] =>
          DefaultFilters.findOptionByName(name, defaultDeploymentId).map(a => a)

        case _ if ofType == classOf[DefaultRouting] =>
          DefaultRoutings.findOptionByName(name, defaultDeploymentId) match {
            case Some(r) => Some(defaultRoutingModel2Artifact(r))
            case None => None
          }
        case _ if ofType == classOf[DefaultScale] =>
          DefaultScales.findOptionByName(name, defaultDeploymentId).map(a => a)

        case _ if ofType == classOf[DefaultSla] =>
          DefaultSlas.findOptionByName(name, defaultDeploymentId) match {
            case Some(s) =>
              Some(DefaultSla(name = VampPersistenceUtil.restoreToAnonymous(s.name, s.isAnonymous), `type` = s.slaType, escalations = escalations2Artifacts(s.escalationReferences), parameters = parametersToArtifact(s.parameters)))
            case None => None
          }

        case _ if ofType == classOf[DefaultBreed] =>
          DefaultBreeds.findOptionByName(name, defaultDeploymentId) match {
            case Some(b) => Some(defaultBreedModel2DefaultBreedArtifact(b))
            case None => None
          }

        case _ => throw exception(UnsupportedPersistenceRequest(ofType))
      }
    }

    private def escalations2Artifacts(escalationReferences: List[EscalationReferenceModel]): List[Escalation] =
      escalationReferences.map(esc =>
        if (esc.isDefinedInline)
          read(esc.name, classOf[DefaultEscalation]) match {
            case Some(escalation: DefaultEscalation) => escalation
            case _ => EscalationReference(esc.name)
          }
        else
          EscalationReference(esc.name)
      )

    private def createParameters(parameters: Map[String, Any], parentId: Int, parentType: ParameterParentType): Unit = {
      parameters.map(param =>
        param._2 match {
          case i: Int => Parameters.add(ParameterModel(deploymentId = None, name = param._1, intValue = i, parameterType = ParameterType.Int, parentType = parentType, parentId = parentId))
          case d: Double => Parameters.add(ParameterModel(deploymentId = None, name = param._1, doubleValue = d, parameterType = ParameterType.Double, parentType = parentType, parentId = parentId))
          case s: String => Parameters.add(ParameterModel(deploymentId = None, name = param._1, stringValue = Some(s), parameterType = ParameterType.String, parentType = parentType, parentId = parentId))
          case e => throw exception(UnsupportedPersistenceRequest(s"Invalid parameter for $parentType with name $parentId for type ${e.getClass}"))
        }
      )
    }

    private def createBreedReference(artifact: Breed, deploymentId: Option[Int]): String = artifact match {
      case breed: DefaultBreed =>
        val savedName = createOrUpdateBreed(DeploymentDefaultBreed(deploymentId, breed)).name
        BreedReferences.add(BreedReferenceModel(deploymentId = deploymentId, name = savedName, isDefinedInline = true))
        savedName
      case breed: BreedReference =>
        BreedReferences.add(BreedReferenceModel(deploymentId = deploymentId, name = breed.name, isDefinedInline = true))
        breed.name
    }

    private def createScaleReference(artifact: Option[Scale], deploymentId: Option[Int]): Option[Int] = artifact match {
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

    private def createRoutingReference(artifact: Option[Routing], deploymentId: Option[Int]): Option[Int] = artifact match {
      case Some(routing: DefaultRouting) =>
        DefaultRoutings.findOptionByName(routing.name, deploymentId) match {
          case Some(existing) => updateRouting(existing, routing)
            Some(RoutingReferences.add(RoutingReferenceModel(deploymentId = deploymentId, name = existing.name, isDefinedInline = true)))
          case None =>
            val routingName = createDefaultRoutingModelFromArtifact(DeploymentDefaultRouting(deploymentId, routing)).name
            Some(RoutingReferences.add(RoutingReferenceModel(deploymentId = deploymentId, name = routingName, isDefinedInline = true)))
        }
      case Some(routing: RoutingReference) =>
        Some(RoutingReferences.add(RoutingReferenceModel(deploymentId = deploymentId, name = routing.name, isDefinedInline = false)))
      case _ => None
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

    private def createSla(clusterSla: Option[Sla], deploymentId: Option[Int]): Option[String] = clusterSla match {
      case Some(sla: DeploymentDefaultSla) =>
        val defaultSlaName = DefaultSlas.findOptionByName(sla.name, deploymentId) match {
          case Some(existingSla) =>
            updateSla(existingSla, sla.artifact)
            existingSla.name
          case None =>
            createDefaultSlaModelFromArtifact(sla).name
        }
        SlaReferences.add(SlaReferenceModel(deploymentId = deploymentId, name = defaultSlaName, isDefinedInline = true))
        Some(defaultSlaName)
      case Some(sla: SlaReference) =>
        val slaRefId = SlaReferences.add(SlaReferenceModel(deploymentId = deploymentId, name = sla.name, isDefinedInline = false))
        createEscalationReferences(sla.escalations, None, Some(slaRefId), deploymentId)
        Some(sla.name)
      case _ => None
    }

    private def createBlueprintClusters(clusters: List[Cluster], blueprintId: Int, deploymentId: Option[Int]): Unit = {
      for (cluster <- clusters) {
        val slaRefName: Option[String] = createSla(cluster.sla, deploymentId)
        val clusterId = Clusters.add(ClusterModel(deploymentId = deploymentId, name = cluster.name, blueprintId = blueprintId, slaReference = slaRefName))
        createServices(cluster.services, clusterId, deploymentId)
      }
    }

    private def createDefaultSlaModelFromArtifact(a: DeploymentDefaultSla): DefaultSlaModel = {
      val storedSlaId = DefaultSlas.add(a)
      createParameters(a.artifact.parameters, storedSlaId, ParameterParentType.Sla)
      createEscalationReferences(escalations = a.artifact.escalations, slaId = Some(storedSlaId), slaRefId = None, a.deploymentId)
      DefaultSlas.findById(storedSlaId)
    }

    private def createDefaultRoutingModelFromArtifact(artifact: DeploymentDefaultRouting): DefaultRoutingModel = {
      val routingId = DefaultRoutings.add(artifact)
      createFilterReferences(artifact, routingId)
      DefaultRoutings.findById(routingId)
    }

    private def createDefaultScaleModelFromArtifact(artifact: DeploymentDefaultScale): DefaultScaleModel =
      DefaultScales.findById(DefaultScales.add(artifact))

    private def createTraitNameParameters(parameters: Map[Trait.Name, Any], parentId: Option[Int], parentType: TraitParameterParentType): Unit = {
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

    private def traitNameParametersToArtifactMap(traitNames: List[TraitNameParameterModel]): Map[Trait.Name, Any] = (
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


    private def createDeploymentClusters(clusters: List[DeploymentCluster], deploymentId: Option[Int]): Unit = {
      for (cluster <- clusters) {
        val slaRef = createSla(cluster.sla, deploymentId)
        val clusterId = DeploymentClusters.add(DeploymentClusterModel(name = cluster.name, slaReference = slaRef, deploymentId = deploymentId))
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
          val serviceId = DeploymentServices.add(
            DeploymentServiceModel(
              clusterId = clusterId,
              name = s"service-for-${cluster.name}", // Dummy name
              deploymentId = deploymentId,
              breed = breedId,
              scale = scaleId,
              routing = routingId,
              deploymentState = service.state,
              deploymentTime = service.state.startedAt,
              message = None)
          )
          for (dep <- service.dependencies) DeploymentServiceDependencies.add(DeploymentServiceDependencyModel(name = dep._1, value = dep._2, serviceId = serviceId))
          for (server <- service.servers) {
            val serverId = DeploymentServers.add(DeploymentServerModel(serviceId = serviceId, name = server.name, host = server.host, deployed = server.deployed, deploymentId = deploymentId))
            for (port <- server.ports) ServerPorts.add(ServerPortModel(portIn = port._1, portOut = port._2, serverId = serverId))
          }
        }
      }
    }

    private def addArtifact(artifact: Artifact): Artifact = {
      val nameOfArtifact: String = artifact match {

        case a: Deployment =>
          val deploymentId = Deployments.add(a)
          createDeploymentClusters(a.clusters, Some(deploymentId))
          createPorts(a.endpoints, Some(deploymentId), Some(PortParentType.Deployment))
          createTraitNameParameters(a.parameters, Some(deploymentId), TraitParameterParentType.Deployment)
          Deployments.findById(deploymentId).name

        case a: DefaultBlueprint =>
          val deploymentId: Option[Int] = None
          val blueprintId = DefaultBlueprints.add(DeploymentDefaultBlueprint(deploymentId, a))
          createBlueprintClusters(a.clusters, blueprintId, deploymentId)
          createPorts(ports = a.endpoints, parentId = Some(blueprintId), parentType = Some(PortParentType.BlueprintEndpoint))
          createTraitNameParameters(a.parameters, Some(blueprintId), TraitParameterParentType.Blueprint)
          DefaultBlueprints.findById(blueprintId).name

        case a: DefaultEscalation =>
          createEscalationFromArtifact(DeploymentDefaultEscalation(None, a)).name

        case a: DefaultFilter =>
          DefaultFilters.findById(DefaultFilters.add(DeploymentDefaultFilter(None, a))).name

        case a: DefaultRouting =>
          createDefaultRoutingModelFromArtifact(DeploymentDefaultRouting(None, a)).name

        case a: DefaultScale =>
          createDefaultScaleModelFromArtifact(DeploymentDefaultScale(None, a)).name

        case a: DefaultSla =>
          createDefaultSlaModelFromArtifact(DeploymentDefaultSla(None, a)).name

        case a: DefaultBreed =>
          createOrUpdateBreed(DeploymentDefaultBreed(None, a)).name

        case _ => throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
      readToArtifact(nameOfArtifact, artifact.getClass) match {
        case Some(result) => result
        case _ => throw exception(PersistenceOperationFailure(artifact.getClass))
      }
    }

    private def createEscalationReferences(escalations: List[Escalation], slaId: Option[Int], slaRefId: Option[Int], deploymentId: Option[Int]): Unit = {
      for (escalation <- escalations) {
        escalation match {
          case e: DefaultEscalation =>
            DefaultEscalations.findOptionByName(e.name, deploymentId) match {
              case Some(existing) => updateEscalation(DeploymentDefaultEscalation(deploymentId, e))
              case None => createEscalationFromArtifact(DeploymentDefaultEscalation(deploymentId, e))
            }
            EscalationReferences.add(EscalationReferenceModel(deploymentId = deploymentId, name = e.name, slaId = slaId, slaRefId = slaRefId, isDefinedInline = true))
          case e: EscalationReference =>
            EscalationReferences.add(EscalationReferenceModel(deploymentId = deploymentId, name = e.name, slaId = slaId, slaRefId = slaRefId, isDefinedInline = false))
        }
      }
    }

    private def createEscalationFromArtifact(a: DeploymentDefaultEscalation): DefaultEscalationModel = {
      val storedEscalation = DefaultEscalations.findById(DefaultEscalations.add(a))
      createParameters(a.artifact.parameters, storedEscalation.id.get, ParameterParentType.Escalation)
      storedEscalation
    }

    private def createOrUpdateBreed(a: DeploymentDefaultBreed): DefaultBreedModel = {
      val breedId: Int =
        DefaultBreeds.findOptionByName(a.artifact.name, a.deploymentId) match {
          case Some(existingBreed) =>
            existingBreed.copy(deployable = a.artifact.deployable.name).update
            existingBreed.id.get
          case None => DefaultBreeds.add(a)
        }
      val parentBreed = DefaultBreeds.findById(breedId)
      createBreedChildren(parentBreed, a)
      DefaultBreeds.findById(breedId)
    }

    private def createPorts(ports: List[Port], parentId: Option[Int], parentType: Option[PortParentType]): Unit = {
      for (port <- ports) Ports.add(port2PortModel(port).copy(parentId = parentId, parentType = parentType))
    }

    private def createBreedChildren(parentBreedModel: DefaultBreedModel, a: DeploymentDefaultBreed): Unit = {
      for (env <- a.artifact.environmentVariables) {
        EnvironmentVariables.add(EnvironmentVariableModel(deploymentId = None, name = env.name.value, alias = env.alias, direction = env.direction, value = env.value, parentId = parentBreedModel.id, parentType = Some(EnvironmentVariableParentType.Breed)))
      }
      createPorts(a.artifact.ports, parentBreedModel.id, parentType = Some(PortParentType.Breed))
      for (dependency <- a.artifact.dependencies) {
        dependency._2 match {
          case db: DefaultBreed =>
            val savedName = createOrUpdateBreed(DeploymentDefaultBreed(a.deploymentId, db)).name
            Dependencies.add(DependencyModel(deploymentId = a.deploymentId, name = dependency._1, breedName = savedName, isDefinedInline = true, parentId = parentBreedModel.id.get))
          case br: BreedReference =>
            Dependencies.add(DependencyModel(deploymentId = a.deploymentId, name = dependency._1, breedName = br.name, isDefinedInline = false, parentId = parentBreedModel.id.get))
        }
      }
    }

    private def createFilterReferences(a: DeploymentDefaultRouting, routingId: DefaultRoutingModel#Id): Unit = {
      for (filter <- a.artifact.filters) {
        filter match {
          case f: DefaultFilter =>
            val filterId: Int = DefaultFilters.findOptionByName(f.name, a.deploymentId) match {
              case Some(existing) =>
                DefaultFilters.update(existing.copy(condition = f.condition))
                existing.id.get
              case _ =>
                DefaultFilters.add(DeploymentDefaultFilter(a.deploymentId, f))
            }
            val defFilter = DefaultFilters.findById(filterId)
            FilterReferences.add(FilterReferenceModel(deploymentId = a.deploymentId, name = defFilter.name, routingId = routingId, isDefinedInline = true))

          case f: FilterReference =>
            FilterReferences.add(FilterReferenceModel(deploymentId = a.deploymentId, name = filter.name, routingId = routingId, isDefinedInline = false))
        }
      }
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
          for (dependency <- service.dependencies) {
            DeploymentServiceDependencies.deleteById(dependency.id.get)
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
          case Some(slaName) => DefaultSlas.findOptionByName(slaName, deploymentId) match {
            case Some(slaModel) => deleteSlaModel(slaModel)
            case _ =>
          }
            SlaReferences.deleteByName(slaName, deploymentId)
          case _ =>
        }
      }
    }

    private def deleteDeploymentModel(m: DeploymentModel): Unit = {
      deleteDeploymentClusters(m.clusters, m.id)
      deleteModelPorts(m.endpoints)
      deleteModelTraitNameParameters(m.parameters)
      Deployments.deleteById(m.id.get)
    }

    private def deleteArtifact(artifact: Artifact): Unit = {
      artifact match {

        case _: Deployment =>
          Deployments.findOptionByName(artifact.name) match {
            case Some(deployment) => deleteDeploymentModel(deployment)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _: DefaultBlueprint =>
          DefaultBlueprints.findOptionByName(artifact.name, None) match {
            case Some(blueprint) => deleteBlueprintModel(blueprint)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _: DefaultEscalation =>
          DefaultEscalations.findOptionByName(artifact.name, None) match {
            case Some(escalation) =>
              deleteEscalationModel(escalation)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _: DefaultFilter =>
          DefaultFilters.deleteByName(artifact.name, None)

        case _: DefaultRouting =>
          DefaultRoutings.findOptionByName(artifact.name, None) match {
            case Some(routing) =>
              deleteRoutingModel(routing)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _: DefaultScale =>
          DefaultScales.deleteByName(artifact.name, None)

        case _: DefaultSla => DefaultSlas.findOptionByName(artifact.name, None) match {
          case Some(sla) =>
            deleteSlaModel(sla)
          case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
        }
        case _: DefaultBreed =>
          DefaultBreeds.findOptionByName(artifact.name, None) match {
            case Some(breed: DefaultBreedModel) =>
              deleteDefaultBreedModel(breed)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _ => throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
    }

    private def deleteBlueprintModel(blueprint: DefaultBlueprintModel): Unit = {
      deleteBlueprintClusters(blueprint.clusters)
      deleteModelTraitNameParameters(blueprint.parameters)
      deleteModelPorts(blueprint.endpoints)
      DefaultBlueprints.deleteById(blueprint.id.get)
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
            DefaultSlas.findOptionByName(slaRef, cluster.deploymentId) match {
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


    private def deleteSlaModelChildObjects(sla: DefaultSlaModel): Unit = {
      for (escalationRef <- sla.escalationReferences) {
        DefaultEscalations.findOptionByName(escalationRef.name, escalationRef.deploymentId) match {
          case Some(escalation) if escalation.isAnonymous => deleteEscalationModel(escalation)
          case Some(escalation) =>
          case None => // Should not happen
        }
        EscalationReferences.deleteById(escalationRef.id.get)
      }
      deleteExistingParameters(sla.parameters)
    }

    private def deleteSlaModel(sla: DefaultSlaModel) {
      deleteSlaModelChildObjects(sla)
      DefaultSlas.deleteById(sla.id.get)
    }

    private def deleteEscalationModel(escalation: DefaultEscalationModel): Unit = {
      for (param <- escalation.parameters) Parameters.deleteById(param.id.get)
      DefaultEscalations.deleteById(escalation.id.get)
    }


    private def deleteRoutingModel(routing: DefaultRoutingModel): Unit = {
      deleteFilterReferences(routing.filterReferences)
      DefaultRoutings.deleteById(routing.id.get)
    }

    // Delete breed and all anonymous artifact in the hierarchy
    private def deleteDefaultBreedModel(breed: DefaultBreedModel): Unit = {
      for (port <- breed.ports) Ports.deleteById(port.id.get)
      for (envVar <- breed.environmentVariables) EnvironmentVariables.deleteById(envVar.id.get)
      for (dependency <- breed.dependencies) {
        val depModel = Dependencies.findById(dependency.id.get)
        if (depModel.isDefinedInline) {
          DefaultBreeds.findOptionByName(depModel.breedName, breed.deploymentId) match {
            case Some(childBreed) if childBreed.isAnonymous => deleteDefaultBreedModel(childBreed) // Here is the recursive bit
            case Some(childBreed) =>
            case None => // Should not happen
          }
        }
        Dependencies.deleteById(depModel.id.get)
      }
      DefaultBreeds.deleteById(breed.id.get)
    }

  }

}

