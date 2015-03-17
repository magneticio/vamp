package io.magnetic.vamp_core.persistence.store

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Trait.Name
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.persistence.notification._
import io.magnetic.vamp_core.persistence.slick.components.Components
import io.magnetic.vamp_core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.magnetic.vamp_core.persistence.slick.model.PortParentType.PortParentType
import io.magnetic.vamp_core.persistence.slick.model._
import io.magnetic.vamp_core.persistence.slick.util.VampPersistenceUtil
import org.slf4j.LoggerFactory

import scala.slick.jdbc.JdbcBackend._


/**
 * JDBC storage of artifacts
 */
trait JdbcStoreProvider extends StoreProvider with PersistenceNotificationProvider {
  this: ExecutionContextProvider =>


  val db: Database = Database.forConfig("persistence.jdbcProvider")
  implicit val sess = db.createSession()
  private val logger = Logger(LoggerFactory.getLogger(classOf[JdbcStoreProvider]))

  override val store: Store = new JdbcStore()


  private class JdbcStore extends Store {
    import io.magnetic.vamp_core.persistence.slick.components.Components.instance._
    import io.magnetic.vamp_core.persistence.slick.model.Implicits._

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
        //TODO implement deployment types
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

    def defaultRoutingModel2DefaultRoutingArtifact(r: DefaultRoutingModel): DefaultRouting = {
      val filters: List[Filter] = r.filterReferences.map(filter =>
        if (filter.isDefinedInline)
          defaultFilterModel2Artifact(DefaultFilters.findByName(filter.name))
        else
          FilterReference(filter.name)
      )
      DefaultRouting(name = VampPersistenceUtil.restoreToAnonymous(r.name, r.isAnonymous), weight = r.weight, filters = filters)
    }

    def defaultBreedModel2DefaultBreedArtifact(b: DefaultBreedModel): DefaultBreed = {
      val envs = b.environmentVariables.map(e => environmentVariableModel2Artifact(e))
      val dependencies = for {
        d <- b.dependencies
        breedRef = if (d.isDefinedInline) {
          readToArtifact(d.breedName, classOf[DefaultBreed]) match {
            case Some(childBreed: DefaultBreed) => childBreed
            case Some(childBreed: BreedReference) => childBreed
            case _ => BreedReference(d.breedName) //Not found, return a reference instead
          }
        } else {
          BreedReference(d.breedName)
        }
      } yield d.name -> breedRef
      DefaultBreed(name = VampPersistenceUtil.restoreToAnonymous(b.name, b.isAnonymous),
        deployable = Deployable(b.deployable),
        ports = readPortsToArtifactList(b.ports),
        environmentVariables = envs,
        dependencies = dependencies.toMap)
    }

    private def deleteExistingParameters(parameters: List[ParameterModel]): Unit =
      for (param <- parameters) Parameters.deleteById(param.id.get)

    private def deleteFilterReferences(filterReferences: List[FilterReferenceModel]): Unit = {
      for (filter <- filterReferences) {
        if (filter.isDefinedInline) {
          DefaultFilters.findOptionByName(filter.name) match {
            case Some(storedFilter) if storedFilter.isAnonymous => DefaultFilters.deleteByName(filter.name)
            case _ =>
          }
        }
        FilterReferences.deleteByName(filter.name)
      }
    }

    private def updateSla(existing: DefaultSlaModel, a: DefaultSla): Unit = {
      deleteSlaModelChildObjects(existing)
      createEscalationReferences(a.escalations, existing.id, None)
      createParameters(a.parameters, a.name, ParameterParentType.Sla)
      existing.copy(slaType = a.`type`).update
    }

    private def updateBreed(existing: DefaultBreedModel, artifact: DefaultBreed): Unit = {
      for (d <- existing.dependencies) {
        if (d.isDefinedInline) {
          DefaultBreeds.findOptionByName(d.breedName) match {
            case Some(breed) => if (breed.isAnonymous) deleteDefaultBreedModel(breed)
            case _ =>
          }
        }
        Dependencies.deleteById(d.id.get)
      }
      deleteModelPorts(existing.ports)
      existing.environmentVariables.map(e => EnvironmentVariables.deleteById(e.id.get))
      createBreedChildren(existing, artifact)
      existing.copy(deployable = artifact.deployable.name).update
    }

    private def updateRouting(existing: DefaultRoutingModel, artifact: DefaultRouting): Unit = {
      deleteFilterReferences(existing.filterReferences)
      createFilterReferences(artifact, existing.id.get)
      existing.copy(weight = artifact.weight).update
    }

    private def updateBlueprint(existing: DefaultBlueprintModel, artifact: DefaultBlueprint): Unit = {
      deleteClusters(existing.clusters)
      createClusters(artifact.clusters, existing.id.get)
      deleteModelPorts(existing.endpoints)
      createPorts(artifact.endpoints, existing.id, parentType = Some(PortParentType.BlueprintEndpoint))
      deleteModelTraitNameParameters(existing.parameters)
      createTraitNameParameters(artifact.parameters, existing.id.get)
      existing.update
    }

    private def deleteModelPorts(ports: List[PortModel]): Unit = {
      for (p <- ports) Ports.deleteById(p.id.get)
    }

    private def deleteModelTraitNameParameters(params: List[TraitNameParameterModel]): Unit = {
      for (p <- params) TraitNameParameters.deleteById(p.id.get)
    }

    private def updateScale(existing: DefaultScaleModel, a: DefaultScale): Unit = {
      existing.copy(cpu = a.cpu, memory = a.memory, instances = a.instances).update
    }

    private def updateArtifact(artifact: Artifact): Artifact = {
      artifact match {
        //TODO implement deployment types
        case a: DefaultBlueprint =>
          updateBlueprint(DefaultBlueprints.findByName(a.name), a)

        case a: DefaultEscalation =>
          updateEscalation(a)

        case a: DefaultFilter =>
          DefaultFilters.findByName(a.name).copy(condition = a.condition).update

        case a: DefaultRouting =>
          updateRouting(DefaultRoutings.findByName(a.name), a)

        case a: DefaultScale =>
          updateScale(DefaultScales.findByName(a.name), a)

        case a: DefaultSla =>
          updateSla(DefaultSlas.findByName(a.name), a)

        case a: DefaultBreed =>
          updateBreed(DefaultBreeds.findByName(a.name), a)

        case _ => throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
      read(artifact.name, artifact.getClass).get
    }

    private def updateEscalation(a: DefaultEscalation): Unit = {
      val existing = DefaultEscalations.findByName(a.name)
      deleteExistingParameters(existing.parameters)
      createParameters(a.parameters, a.name, ParameterParentType.Escalation)
      existing.copy(escalationType = a.`type`).update
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

    private def findOptionRoutingArtifactViaReference(artifactName: Option[String]): Option[Routing] = artifactName match {
      case Some(routingRef) =>
        RoutingReferences.findOptionByName(routingRef) match {
          case Some(ref: RoutingReferenceModel) if ref.isDefinedInline =>
            DefaultRoutings.findOptionByName(ref.name) match {
              case Some(defaultRouting) => Some(defaultRoutingModel2DefaultRoutingArtifact(defaultRouting))
              case None => Some(RoutingReference(name = ref.name)) // Not found, return a reference instead
            }
          case Some(ref) => Some(RoutingReference(name = ref.name))
          case None => None
        }
      case None => None
    }

    private def findBreedArtifactViaReference(artifactName: String): Breed =
      BreedReferences.findOptionByName(artifactName) match {
        case Some(breedRef) if breedRef.isDefinedInline =>
          DefaultBreeds.findOptionByName(breedRef.name) match {
            case Some(b) => defaultBreedModel2DefaultBreedArtifact(b)
            case None => BreedReference(name = artifactName) //Not breed not found, return a reference instead
          }
        case _ => BreedReference(name = artifactName)
      }

    private def findOptionSlaArtifactViaReferenceName(artifactName: Option[String]): Option[Sla] = artifactName match {
      case Some(slaName) =>
        SlaReferences.findOptionByName(slaName) match {
          case Some(slaReference) if slaReference.isDefinedInline =>
            readToArtifact(slaReference.name, classOf[DefaultSla]) match {
              case Some(slaArtifact: DefaultSla) => Some(slaArtifact)
              case Some(slaArtifact: SlaReference) => Some(slaArtifact)
              case _ => None
            }
          case Some(slaReference) =>
            Some(SlaReference(name = slaReference.name, escalations = readEscalationsArtifacts(slaReference.escalationReferences)))
          case None => None
        }
      case None => None
    }

    private def findOptionScaleArtifactViaReferenceName(artifactName: Option[String]): Option[Scale] = artifactName match {
      case Some(scaleRef) =>
        ScaleReferences.findOptionByName(scaleRef) match {
          case Some(ref: ScaleReferenceModel) if ref.isDefinedInline =>
            DefaultScales.findOptionByName(ref.name) match {
              case Some(defaultScale) => Some(defaultScale)
              case None => Some(ScaleReference(name = ref.name)) // Not found, return a reference instead
            }
          case Some(ref) => Some(ScaleReference(name = ref.name))
          case None => None
        }
      case None => None
    }

    private def findServicesArtifacts(services: List[ServiceModel]): List[Service] = services.map(service =>
      Service(
        breed = findBreedArtifactViaReference(service.breedReferenceName),
        scale = findOptionScaleArtifactViaReferenceName(service.scaleReferenceName),
        routing = findOptionRoutingArtifactViaReference(service.routingReferenceName)
      )
    )

    private def findClusterArtifacts(clusters: List[ClusterModel]): List[Cluster] =
      clusters.map(cluster => Cluster(
        name = cluster.name,
        services = findServicesArtifacts(cluster.services),
        sla = findOptionSlaArtifactViaReferenceName(cluster.slaReference))
      )


    private def readPortsToArtifactList(ports: List[PortModel]): List[Port] = ports.map(p => portModel2Port(p)).toList

    private def readToArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] = {
      ofType match {
        //TODO implement deployment types
        case _ if ofType == classOf[DefaultBlueprint] =>
          DefaultBlueprints.findOptionByName(name) match {
            case Some(b) => Some(
              DefaultBlueprint(
                name = VampPersistenceUtil.restoreToAnonymous(b.name, b.isAnonymous),
                clusters = findClusterArtifacts(b.clusters),
                endpoints = readPortsToArtifactList(b.endpoints),
                parameters = traitNameParametersToArtifactMap(b.parameters)
              )
            )
            case None => None
          }

        case _ if ofType == classOf[DefaultEscalation] =>
          DefaultEscalations.findOptionByName(name) match {
            case Some(e) =>
              Some(DefaultEscalation(name = VampPersistenceUtil.restoreToAnonymous(e.name, e.isAnonymous), `type` = e.escalationType, parameters = parametersToArtifact(e.parameters)))
            case None => None
          }

        case _ if ofType == classOf[DefaultFilter] =>
          DefaultFilters.findOptionByName(name).map(a => a)

        case _ if ofType == classOf[DefaultRouting] =>
          DefaultRoutings.findOptionByName(name) match {
            case Some(r) => Some(defaultRoutingModel2DefaultRoutingArtifact(r))
            case None => None
          }
        case _ if ofType == classOf[DefaultScale] =>
          DefaultScales.findOptionByName(name).map(a => a)

        case _ if ofType == classOf[DefaultSla] =>
          DefaultSlas.findOptionByName(name) match {
            case Some(s) =>
              Some(DefaultSla(name = VampPersistenceUtil.restoreToAnonymous(s.name, s.isAnonymous), `type` = s.slaType, escalations = readEscalationsArtifacts(s.escalationReferences), parameters = parametersToArtifact(s.parameters)))
            case None => None
          }

        case _ if ofType == classOf[DefaultBreed] =>
          DefaultBreeds.findOptionByName(name) match {
            case Some(b) => Some(defaultBreedModel2DefaultBreedArtifact(b))
            case None => None
          }

        case _ => throw exception(UnsupportedPersistenceRequest(ofType))
      }
    }

    private def readEscalationsArtifacts(escalationReferences: List[EscalationReferenceModel]): List[Escalation] =
      escalationReferences.map(esc =>
        if (esc.isDefinedInline)
          read(esc.name, classOf[DefaultEscalation]) match {
            case Some(escalation: DefaultEscalation) => escalation
            case _ => EscalationReference(esc.name)
          }
        else
          EscalationReference(esc.name)
      )

    private def createParameters(parameters: Map[String, Any], parentName: String, parentType: ParameterParentType): Unit = {
      parameters.map(param =>
        param._2 match {
          case i: Int => Parameters.add(ParameterModel(name = param._1, intValue = i, parameterType = ParameterType.Int, parentType = parentType, parentName = parentName))
          case d: Double => Parameters.add(ParameterModel(name = param._1, doubleValue = d, parameterType = ParameterType.Double, parentType = parentType, parentName = parentName))
          case s: String => Parameters.add(ParameterModel(name = param._1, stringValue = Some(s), parameterType = ParameterType.String, parentType = parentType, parentName = parentName))
          case e => throw exception(UnsupportedPersistenceRequest(s"Invalid parameter for $parentType with name $parentName for type ${e.getClass}"))
        }
      )
    }

    private def createBreedReference(artifact: Breed): String = artifact match {
      case breed: DefaultBreed =>
        val savedName = createOrUpdateBreed(breed: DefaultBreed).name
        BreedReferences.add(BreedReferenceModel(name = savedName, isDefinedInline = true))
        savedName
      case breed: BreedReference =>
        BreedReferences.add(BreedReferenceModel(name = breed.name, isDefinedInline = true))
        breed.name
    }

    private def createScaleReference(artifact: Option[Scale]): Option[String] = artifact match {
      case Some(scale: DefaultScale) =>
        DefaultScales.findOptionByName(scale.name) match {
          case Some(existing) => updateScale(existing, scale)
            Some(existing.name)
          case None =>
            val scaleName = createDefaultScaleModelFromArtifact(scale).name
            ScaleReferences.add(ScaleReferenceModel(name = scaleName, isDefinedInline = true))
            Some(scaleName)
        }
      case Some(scale: ScaleReference) =>
        ScaleReferences.add(ScaleReferenceModel(name = scale.name, isDefinedInline = false))
        Some(scale.name)
      case _ => None
    }

    private def createRoutingReference(artifact: Option[Routing]): Option[String] = artifact match {
      case Some(routing: DefaultRouting) =>
        DefaultRoutings.findOptionByName(routing.name) match {
          case Some(existing) => updateRouting(existing, routing)
            Some(existing.name)
          case None =>
            val routingName = createDefaultRoutingModelFromArtifact(routing).name
            RoutingReferences.add(RoutingReferenceModel(name = routingName, isDefinedInline = true))
            Some(routingName)
        }
      case Some(routing: RoutingReference) =>
        RoutingReferences.add(RoutingReferenceModel(name = routing.name, isDefinedInline = false))
        Some(routing.name)
      case _ => None
    }

    private def createServices(services: List[Service], clusterId: Int): Unit = {
      services.map(service =>
        Services.add(ServiceModel(
          clusterId = clusterId,
          breedReferenceName = createBreedReference(service.breed),
          routingReferenceName = createRoutingReference(service.routing),
          scaleReferenceName = createScaleReference(service.scale))
        )
      )
    }

    private def createClusters(clusters: List[Cluster], blueprintId: Int): Unit = {
      for (cluster <- clusters) {
        val slaRefName: Option[String] = cluster.sla match {
          case Some(sla: DefaultSla) =>
            val defaultSlaName = DefaultSlas.findOptionByName(sla.name) match {
              case Some(existingSla) =>
                updateSla(existingSla, sla)
                existingSla.name
              case None =>
                createDefaultSlaModelFromArtifact(sla).name
            }
            SlaReferences.add(SlaReferenceModel(name = defaultSlaName, isDefinedInline = true))
            Some(defaultSlaName)
          case Some(sla: SlaReference) =>
            val slaRefId = SlaReferences.add(SlaReferenceModel(name = sla.name, isDefinedInline = false))
            createEscalationReferences(sla.escalations, None, Some(slaRefId))
            Some(sla.name)
          case _ => None
        }
        val clusterId = Clusters.add(ClusterModel(name = cluster.name, blueprintId = blueprintId, slaReference = slaRefName))
        createServices(cluster.services, clusterId)
      }
    }

    private def createDefaultSlaModelFromArtifact(a: DefaultSla): DefaultSlaModel = {
      val storedSlaId = DefaultSlas.add(a)
      createParameters(a.parameters, a.name, ParameterParentType.Sla)
      createEscalationReferences(escalations = a.escalations, slaId = Some(storedSlaId), slaRefId = None)
      DefaultSlas.findById(storedSlaId)
    }

    private def createDefaultRoutingModelFromArtifact(artifact: DefaultRouting): DefaultRoutingModel = {
      val routingId = DefaultRoutings.add(artifact)
      createFilterReferences(artifact, routingId)
      DefaultRoutings.findById(routingId)
    }

    private def createDefaultScaleModelFromArtifact(artifact: DefaultScale): DefaultScaleModel =
      DefaultScales.findById(DefaultScales.add(artifact))

    private def createTraitNameParameters(parameters: Map[Trait.Name, Any], parentId: Int): Unit = {
      for (param <- parameters) {
        val prefilledParameter = TraitNameParameterModel(name = param._1.value, scope = param._1.scope, parentId = parentId, groupType = param._1.group)
        param._1.group match {
          case Some(group) if group == Trait.Name.Group.Ports =>
            param._2 match {
              case port: Port =>
                TraitNameParameters.add(prefilledParameter.copy(groupId = Some(Ports.add(port2PortModel(port).copy(parentType = Some(PortParentType.BlueprintParameter))))))
              case env =>
                // Not gone work, if the group is port, the parameter should be too
                throw exception(PersistenceOperationFailure(s"Parameter [${param._1.value}}] of type [${param._1.group}] does not match the supplied parameter [${param._2}}]."))
            }
          case Some(group) if group == Trait.Name.Group.EnvironmentVariables =>
            param._2 match {
              case env: EnvironmentVariable =>
                TraitNameParameters.add(prefilledParameter.copy(
                  groupId = Some(EnvironmentVariables.add(
                    EnvironmentVariableModel(
                      name = env.name.value,
                      alias = env.alias,
                      direction = env.direction,
                      value = env.value,
                      parentId = None,
                      parentType = Some(EnvironmentVariableParentType.BlueprintParameter))
                  )
                  )))
              case env =>
                // Not gone work, if the group is EnvironmentVariable, the parameter should be too
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


    private def addArtifact(artifact: Artifact): Artifact = {
      val nameOfArtifact: String = artifact match {
        //TODO implement deployment types

        case a: DefaultBlueprint =>
          val blueprintId = DefaultBlueprints.add(a)
          createClusters(a.clusters, blueprintId)
          createPorts(ports = a.endpoints, parentId = Some(blueprintId), parentType = Some(PortParentType.BlueprintEndpoint))
          createTraitNameParameters(a.parameters, blueprintId)
          DefaultBlueprints.findById(blueprintId).name

        case a: DefaultEscalation =>
          createEscalation(a).name

        case a: DefaultFilter =>
          DefaultFilters.findById(DefaultFilters.add(a)).name

        case a: DefaultRouting =>
          createDefaultRoutingModelFromArtifact(a).name

        case a: DefaultScale =>
          createDefaultScaleModelFromArtifact(a).name

        case a: DefaultSla =>
          createDefaultSlaModelFromArtifact(a).name

        case a: DefaultBreed =>
          createOrUpdateBreed(a).name

        case _ => throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
      readToArtifact(nameOfArtifact, artifact.getClass) match {
        case Some(result) => result
        case _ => throw exception(PersistenceOperationFailure(artifact.getClass))
      }
    }

    private def createEscalationReferences(escalations: List[Escalation], slaId: Option[Int], slaRefId: Option[Int]): Unit = {
      for (escalation <- escalations) {
        escalation match {
          case e: DefaultEscalation =>
            DefaultEscalations.findOptionByName(e.name) match {
              case Some(existing) => updateEscalation(e)
              case None => createEscalation(e)
            }
            EscalationReferences.add(EscalationReferenceModel(name = e.name, slaId = slaId, slaRefId = slaRefId, isDefinedInline = true))
          case e: EscalationReference =>
            EscalationReferences.add(EscalationReferenceModel(name = e.name, slaId = slaId, slaRefId = slaRefId, isDefinedInline = false))
        }
      }
    }

    private def createEscalation(a: DefaultEscalation): DefaultEscalationModel = {
      val storedEscalation = DefaultEscalations.findById(DefaultEscalations.add(a))
      createParameters(a.parameters, storedEscalation.name, ParameterParentType.Escalation)
      storedEscalation
    }

    private def createOrUpdateBreed(a: DefaultBreed): DefaultBreedModel = {
      val breedId: Int =
        DefaultBreeds.findOptionByName(a.name) match {
          case Some(existingBreed) =>
            existingBreed.copy(deployable = a.deployable.name).update
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

    private def createBreedChildren(parentBreedModel: DefaultBreedModel, a: DefaultBreed): Unit = {
      a.environmentVariables.map(env =>
        EnvironmentVariables.add(EnvironmentVariableModel(name = env.name.value, alias = env.alias, direction = env.direction, value = env.value, parentId = parentBreedModel.id, parentType = Some(EnvironmentVariableParentType.Breed)))
      )
      createPorts(a.ports, parentBreedModel.id, parentType = Some(PortParentType.Breed))
      a.dependencies.map(dependency =>
        dependency._2 match {
          case db: DefaultBreed =>
            val savedName = createOrUpdateBreed(db: DefaultBreed).name
            Dependencies.add(DependencyModel(name = dependency._1, breedName = savedName, isDefinedInline = true, parentBreedName = parentBreedModel.name))
          case br: BreedReference =>
            Dependencies.add(DependencyModel(name = dependency._1, breedName = br.name, isDefinedInline = false, parentBreedName = parentBreedModel.name))
        }
      )
    }

    private def createFilterReferences(a: DefaultRouting, routingId: DefaultRoutingModel#Id): Unit = {
      for (filter <- a.filters) {
        filter match {
          case f: DefaultFilter =>
            val filterId: Int = DefaultFilters.findOptionByName(f.name) match {
              case Some(existing) =>
                DefaultFilters.update(existing.copy(condition = f.condition))
                existing.id.get
              case _ =>
                DefaultFilters.add(f)
            }
            val defFilter = DefaultFilters.findById(filterId)
            FilterReferences.add(FilterReferenceModel(name = defFilter.name, routingId = routingId, isDefinedInline = true))

          case f: FilterReference =>
            FilterReferences.add(FilterReferenceModel(name = filter.name, routingId = routingId, isDefinedInline = false))
        }
      }
    }

    private def deleteArtifact(artifact: Artifact): Unit = {
      artifact match {
        //TODO implement deployment types

        case _: DefaultBlueprint =>
          DefaultBlueprints.findOptionByName(artifact.name) match {
            case Some(blueprint) => deleteBlueprintModel(blueprint)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _: DefaultEscalation =>
          DefaultEscalations.findOptionByName(artifact.name) match {
            case Some(escalation) =>
              deleteEscalationModel(escalation)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _: DefaultFilter =>
          DefaultFilters.deleteByName(artifact.name)

        case _: DefaultRouting =>
          DefaultRoutings.findOptionByName(artifact.name) match {
            case Some(routing) =>
              deleteRoutingModel(routing)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _: DefaultScale =>
          DefaultScales.deleteByName(artifact.name)

        case _: DefaultSla => DefaultSlas.findOptionByName(artifact.name) match {
          case Some(sla) =>
            deleteSlaModel(sla)
          case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
        }
        case _: DefaultBreed =>
          DefaultBreeds.findOptionByName(artifact.name) match {
            case Some(breed: DefaultBreedModel) =>
              deleteDefaultBreedModel(breed)
            case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
          }

        case _ => throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
    }

    private def deleteBlueprintModel(blueprint: DefaultBlueprintModel): Unit = {
      deleteClusters(blueprint.clusters)
      deleteModelTraitNameParameters(blueprint.parameters)
      deleteModelPorts(blueprint.endpoints)
      DefaultBlueprints.deleteById(blueprint.id.get)
    }

    private def deleteClusters(clusters: List[ClusterModel]): Unit = {
      for (cluster <- clusters) {
        cluster.slaReference match {
          case Some(slaRef) =>
            DefaultSlas.findOptionByName(slaRef) match {
              case Some(sla) if sla.isAnonymous => deleteSlaModel(sla)
              case Some(sla) =>
              case None => // Should not happen (log it as not critical)
            }
            SlaReferences.deleteByName(slaRef)
          case None => // Should not happen (log it as not critical)
        }
        for (service <- cluster.services) {
          BreedReferences.findOptionByName(service.breedReferenceName) match {
            case Some(breedRef) =>
              if (breedRef.isDefinedInline)
                DefaultBreeds.findOptionByName(breedRef.name) match {
                  case Some(breed) if breed.isAnonymous => deleteDefaultBreedModel(breed)
                  case Some(breed) =>
                  case None => // Should not happen (log it as not critical)
                }
              BreedReferences.deleteById(breedRef.id.get)
            case None => /// Should not happen (log it as not critical)
          }
          service.scaleReferenceName match {
            case Some(scaleName) =>
              ScaleReferences.findOptionByName(scaleName) match {
                case Some(scaleRef) if scaleRef.isDefinedInline =>
                  DefaultScales.findOptionByName(scaleRef.name) match {
                    case Some(scale) if scale.isAnonymous => DefaultScales.deleteById(scale.id.get)
                    case Some(scale) =>
                    case None => // Should not happen (log it as not critical)
                  }
                case Some(scaleRef) =>
                case None => // Should not happen (log it as not critical)
              }
            case None => // Nothing to delete
          }
          service.routingReferenceName match {
            case Some(routingName) =>
              RoutingReferences.findOptionByName(routingName) match {
                case Some(routingRef) if routingRef.isDefinedInline =>
                  DefaultRoutings.findOptionByName(routingRef.name) match {
                    case Some(routing) if routing.isAnonymous => deleteRoutingModel(routing)
                    case Some(routing) =>
                    case None => // Should not happen (log it as not critical)
                  }
                case Some(scaleRef) =>
                case None => // Should not happen (log it as not critical)
              }
            case None => // Nothing to delete
          }
          Services.deleteById(service.id.get)
        }
        Clusters.deleteById(cluster.id.get)
      }
    }


    private def deleteSlaModelChildObjects(sla: DefaultSlaModel): Unit = {
      for (escalationRef <- sla.escalationReferences) {
        DefaultEscalations.findOptionByName(escalationRef.name) match {
          case Some(escalation) if escalation.isAnonymous => deleteEscalationModel(escalation)
          case Some(escalation) =>
          case None => // Should not happen (log it as not critical)
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
          DefaultBreeds.findOptionByName(depModel.name) match {
            case Some(childBreed) if childBreed.isAnonymous => deleteDefaultBreedModel(childBreed) // Here is the recursive bit
            case Some(childBreed) =>
            case None => // Should not happen (log it as not critical)  logFailedToFindReferencedArtifact(depModel)
          }
        }
        Dependencies.deleteById(depModel.id.get)
      }
      DefaultBreeds.deleteById(breed.id.get)
    }

    //private def logFailedToFindReferencedArtifact(artifact : VampNameablePersistenceModel) = {
    //logger.warn(s"Could not find [${artifact.name}] for ${artifact.getClass}")
    //}

  }

}

