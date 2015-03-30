package io.vamp.core.persistence.store.jdbc

import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider}
import io.vamp.core.persistence.slick.model._
import io.vamp.core.persistence.slick.util.VampPersistenceUtil

import scala.slick.jdbc.JdbcBackend

trait BreedStore extends ParameterStore with PortStore with PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._


  private def removeBreedChildren(existing: DefaultBreedModel): Unit = {
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
  }

  protected def updateBreed(existing: DefaultBreedModel, a: DefaultBreed): Unit = {
    removeBreedChildren(existing)
    createBreedChildren(existing, DeploymentDefaultBreed(existing.deploymentId, a))
    existing.copy(deployable = a.deployable.name).update
  }

  protected def createOrUpdateBreed(a: DeploymentDefaultBreed): DefaultBreedModel = {
    val breedId: Int =
      DefaultBreeds.findOptionByName(a.artifact.name, a.deploymentId) match {
        case Some(existingBreed) =>
          existingBreed.copy(deployable = a.artifact.deployable.name).update
          removeBreedChildren(existingBreed)
          existingBreed.id.get
        case None =>
          DefaultBreeds.add(a)
      }
    val newBreed = DefaultBreeds.findById(breedId)
    createBreedChildren(newBreed, a)
    newBreed
  }

  // Delete breed and all anonymous artifact in the hierarchy
  protected def deleteDefaultBreedModel(breed: DefaultBreedModel): Unit = {
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

  protected def defaultBreedModel2DefaultBreedArtifact(b: DefaultBreedModel): DefaultBreed =
    DefaultBreed(name = VampPersistenceUtil.restoreToAnonymous(b.name, b.isAnonymous),
      deployable = Deployable(b.deployable),
      ports = readPortsToArtifactList(b.ports),
      environmentVariables = b.environmentVariables.map(e => environmentVariableModel2Artifact(e)),
      dependencies = breedDependencies2Artifact(b.dependencies))

  protected def findBreedOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    DefaultBreeds.findOptionByName(name, defaultDeploymentId) match {
      case Some(b) => Some(defaultBreedModel2DefaultBreedArtifact(b))
      case None => None
    }
  }

  protected def deleteBreedFromDb(artifact: DefaultBreed): Unit = {
    DefaultBreeds.findOptionByName(artifact.name, None) match {
      case Some(breed: DefaultBreedModel) =>
        deleteDefaultBreedModel(breed)
      case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
    }

  }

  protected def createBreedArtifact(art: Breed): String = art match {
    case a: DefaultBreed => createOrUpdateBreed(DeploymentDefaultBreed(None, a)).name
  }

  protected def createBreedReference(artifact: Breed, deploymentId: Option[Int]): Int = artifact match {
    case breed: DefaultBreed =>
      val savedName = createOrUpdateBreed(DeploymentDefaultBreed(deploymentId, breed)).name
      BreedReferences.add(BreedReferenceModel(deploymentId = deploymentId, name = savedName, isDefinedInline = true))
    case breed: BreedReference =>
      BreedReferences.add(BreedReferenceModel(deploymentId = deploymentId, name = breed.name, isDefinedInline = true))
  }

  private def createBreedChildren(parentBreedModel: DefaultBreedModel, a: DeploymentDefaultBreed): Unit = {
    for (env <- a.artifact.environmentVariables) {
      EnvironmentVariables.add(EnvironmentVariableModel(deploymentId = None, name = env.name.value, scope = env.name.scope, groupType = env.name.group, alias = env.alias, direction = env.direction, value = env.value, parentId = parentBreedModel.id, parentType = Some(EnvironmentVariableParentType.Breed)))
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

  protected def findBreedArtifactViaReferenceId(referenceId: Int, deploymentId: Option[Int]): Breed =
    BreedReferences.findById(referenceId) match {
      case breedRef if breedRef.isDefinedInline =>
        DefaultBreeds.findOptionByName(breedRef.name, deploymentId) match {
          case Some(b) => defaultBreedModel2DefaultBreedArtifact(b)
          case None => BreedReference(name = breedRef.name) //Not found, return a reference instead
        }
      case breedRef => BreedReference(name = breedRef.name)
    }

  private def breedDependencies2Artifact(dependencies: List[DependencyModel]): Map[String, Breed] = (for {
    d <- dependencies
    breedRef = if (d.isDefinedInline) {
      findBreedOptionArtifact(d.breedName) match {
        case Some(childBreed: DefaultBreed) => childBreed
        case Some(childBreed: BreedReference) => childBreed
        case _ => BreedReference(d.breedName) //Not found, return a reference instead
      }
    } else {
      BreedReference(d.breedName)
    }
  } yield d.name -> breedRef).toMap


}
