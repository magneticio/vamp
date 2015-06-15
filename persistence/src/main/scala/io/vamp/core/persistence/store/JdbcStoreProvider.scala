package io.vamp.core.persistence.store

import com.typesafe.scalalogging.Logger
import io.vamp.common.http.OffsetEnvelope
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider, PersistenceOperationFailure, UnsupportedPersistenceRequest}
import io.vamp.core.persistence.slick.components.Components
import io.vamp.core.persistence.slick.extension.Nameable
import io.vamp.core.persistence.slick.model.DeploymentGenericEscalation
import io.vamp.core.persistence.store.jdbc._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.language.existentials
import scala.slick.jdbc.JdbcBackend
import scala.slick.jdbc.JdbcBackend._

case class JdbcStoreInfo(`type`: String, url: String, database: DatabaseInfo)

case class DatabaseInfo(name: String, version: String, schemaVersion: String)

/**
 * JDBC storage of artifacts
 */
class JdbcStoreProvider(executionContext: ExecutionContext) extends StoreProvider with PersistenceNotificationProvider {

  val db: Database = Database.forConfig("vamp.core.persistence.jdbc.provider")
  implicit val sess = db.createSession()

  override val store: Store = new JdbcStore()

  private class JdbcStore(implicit val sess: JdbcBackend.Session) extends Store with ScaleStore with PortStore
  with DeploymentStore with BlueprintStore with BreedStore
  with RoutingStore with FilterStore
  with EnvironmentVariableStore
  with SlaStore with EscalationStore with ParameterStore {

    import io.vamp.core.persistence.slick.components.Components.instance._

    private val logger = Logger(LoggerFactory.getLogger(classOf[JdbcStoreProvider]))

    Components.instance.upgradeSchema

    def info = JdbcStoreInfo(
      "jdbc",
      sess.conn.getMetaData.getURL,
      DatabaseInfo(sess.conn.getMetaData.getDatabaseProductName, sess.conn.getMetaData.getDatabaseProductVersion, Components.instance.schemaInfo(sess))
    )

    def create(artifact: Artifact, ignoreIfExists: Boolean): Artifact = {
      logger.debug(s"create [$ignoreIfExists] $artifact")
      read(artifact.name, artifact.getClass) match {
        case None => createArtifact(artifact)
        case Some(storedArtifact) if !ignoreIfExists => update(artifact, create = false)
        case Some(storedArtifact) if ignoreIfExists => storedArtifact
      }
    }

    def read(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] = {
      findArtifact(name, ofType)
    }

    def update(artifact: Artifact, create: Boolean): Artifact = {
      logger.debug(s"update [$create] $artifact")
      read(artifact.name, artifact.getClass) match {
        case None =>
          if (create) this.createArtifact(artifact)
          else throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
        case Some(existingArtifact) => updateArtifact(artifact)
      }
    }

    def delete(name: String, ofType: Class[_ <: Artifact]): Artifact = {
      findArtifact(name, ofType) match {
        case Some(artifact) =>
          deleteArtifact(artifact)
          artifact
        case None =>
          throw exception(ArtifactNotFound(name, ofType))
      }
    }

    def all(`type`: Class[_ <: Artifact]): List[_ <: Artifact] = queryAndTypeFor(`type`) match {
      case (query, ofType) => query.fetchAll.map(artifact => read(artifact.name, ofType).get)
    }

    def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = queryAndTypeFor(`type`) match {
      case (query, ofType) =>
        val total = query.count
        val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
        val artifacts = query.pagedList((p - 1) * pp, pp).map(artifact => read(artifact.name, ofType).get)
        val (rp, rpp) = OffsetEnvelope.normalize(total, p, pp, ArtifactResponseEnvelope.maxPerPage)
        ArtifactResponseEnvelope(artifacts, total, rp, rpp)
    }

    private def queryAndTypeFor(ofType: Class[_ <: Artifact]): (EntityTableQuery[_ <: Nameable[_], _], Class[_ <: Artifact]) = {
      ofType match {
        case _ if ofType == classOf[Deployment] => Deployments -> ofType
        case _ if ofType == classOf[DefaultBlueprint] || ofType == classOf[Blueprint] => DefaultBlueprints -> classOf[DefaultBlueprint]
        case _ if ofType == classOf[GenericEscalation] || ofType == classOf[Escalation] ||
          ofType == classOf[ScaleInstancesEscalation] || ofType == classOf[ScaleCpuEscalation] || ofType == classOf[ScaleMemoryEscalation] ||
          ofType == classOf[ToOneEscalation] || ofType == classOf[ToAllEscalation] => GenericEscalations -> classOf[GenericEscalation]
        case _ if ofType == classOf[DefaultFilter] || ofType == classOf[Filter] => DefaultFilters -> classOf[DefaultFilter]
        case _ if ofType == classOf[DefaultRouting] || ofType == classOf[Routing] => DefaultRoutings -> classOf[DefaultRouting]
        case _ if ofType == classOf[DefaultScale] || ofType == classOf[Scale] => DefaultScales -> classOf[DefaultScale]
        case _ if ofType == classOf[GenericSla] || ofType == classOf[EscalationOnlySla] || ofType == classOf[ResponseTimeSlidingWindowSla] || ofType == classOf[Sla] => GenericSlas -> classOf[GenericSla]
        case _ if ofType == classOf[DefaultBreed] || ofType == classOf[Breed] => DefaultBreeds -> classOf[DefaultBreed]
        case _ =>
          logger.error(s"Unsupported Persistence Request - All - $ofType")
          throw exception(UnsupportedPersistenceRequest(ofType))
      }
    }

    private def updateArtifact(artifact: Artifact): Artifact = {
      val deploymentId: Option[Int] = None
      artifact match {

        case a: Deployment =>
          updateDeployment(Deployments.findByName(a.name), a)

        case a: DefaultBlueprint =>
          updateBlueprint(DefaultBlueprints.findByName(a.name, deploymentId), a)

        case a: Escalation =>
          updateEscalation(DeploymentGenericEscalation(deploymentId, a))

        case a: DefaultFilter =>
          DefaultFilters.findByName(a.name, deploymentId).copy(condition = a.condition).update

        case a: DefaultRouting =>
          updateRouting(DefaultRoutings.findByName(a.name, deploymentId), a)

        case a: DefaultScale =>
          updateScale(DefaultScales.findByName(a.name, deploymentId), a)

        case a: Sla =>
          updateSla(GenericSlas.findByName(a.name, deploymentId), a)

        case a: DefaultBreed =>
          updateBreed(DefaultBreeds.findByName(a.name, deploymentId), a)

        case _ =>
          logger.error(s"Unsupported Persistence Request - Update - ${artifact.getClass}")
          throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
      read(artifact.name, artifact.getClass).get
    }

    private def findArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] =
      ofType match {

        case _ if ofType == classOf[Deployment] =>
          findDeploymentOptionArtifact(name)

        case _ if ofType == classOf[DefaultBlueprint] || ofType == classOf[Blueprint] =>
          findBlueprintOptionArtifact(name)

        case _ if ofType == classOf[GenericEscalation] || ofType == classOf[Escalation] ||
          ofType == classOf[ScaleInstancesEscalation] || ofType == classOf[ScaleCpuEscalation] || ofType == classOf[ScaleMemoryEscalation] ||
          ofType == classOf[ToOneEscalation] || ofType == classOf[ToAllEscalation] =>
          findEscalationOptionArtifact(name)

        case _ if ofType == classOf[DefaultFilter] || ofType == classOf[Filter] =>
          findFilterOptionArtifact(name)

        case _ if ofType == classOf[DefaultRouting] || ofType == classOf[Routing] =>
          findRoutingOptionArtifact(name)

        case _ if ofType == classOf[DefaultScale] || ofType == classOf[Scale] =>
          findScaleOptionArtifact(name)

        case _ if ofType == classOf[GenericSla] || ofType == classOf[ResponseTimeSlidingWindowSla] || ofType == classOf[EscalationOnlySla] || ofType == classOf[Sla] =>
          findSlaOptionArtifact(name)

        case _ if ofType == classOf[DefaultBreed] || ofType == classOf[Breed] =>
          findBreedOptionArtifact(name)

        case _ =>
          logger.error(s"Unsupported Persistence Request - Find - $ofType")
          throw exception(UnsupportedPersistenceRequest(ofType))
      }


    private def createArtifact(artifact: Artifact): Artifact = {
      val nameOfArtifact: String = artifact match {
        case a: Deployment => createDeploymentArtifact(a)

        case art: Blueprint => createBlueprintArtifact(art)

        case art: Escalation => createEscalationArtifact(art)

        case art: Filter => createFilterArtifact(art)

        case art: Routing => createRoutingArtifact(art)

        case art: Scale => createScaleArtifact(art)

        case art: Sla => createSlaArtifact(art)

        case art: Breed => createBreedArtifact(art)

        case _ =>
          logger.info(s"Unsupported Persistence Request - Create - ${artifact.getClass}")
          throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
      findArtifact(nameOfArtifact, artifact.getClass) match {
        case Some(result) => result
        case _ => throw exception(PersistenceOperationFailure(artifact.getClass))
      }
    }

    private def deleteArtifact(artifact: Artifact): Unit = {
      artifact match {

        case deployment: Deployment => deleteDeploymentFromDb(deployment)

        case blueprint: DefaultBlueprint => deleteBlueprintFromDb(blueprint)

        case esc: Escalation => deleteEscalationFromDb(esc)

        case filter: DefaultFilter => deleteFilterFromDb(filter)

        case routing: DefaultRouting => deleteRoutingFromDb(routing)

        case scale: DefaultScale => deleteScaleFromDb(scale)

        case sla: Sla => deleteSlaFromDb(sla)

        case breed: DefaultBreed => deleteBreedFromDb(breed)

        case _ =>
          logger.error(s"Unsupported Persistence Request - Delete - ${artifact.getClass}")
          throw exception(UnsupportedPersistenceRequest(artifact.getClass))
      }
    }


  }

}