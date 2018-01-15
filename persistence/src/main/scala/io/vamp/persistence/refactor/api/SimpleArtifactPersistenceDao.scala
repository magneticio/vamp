package io.vamp.persistence.refactor.api

import akka.actor.ActorSystem
import io.vamp.common.{Id, Namespace}
import io.vamp.model.artifact.{Deployment, Gateway}
import io.vamp.persistence.refactor.serialization.SerializationSpecifier

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by mihai on 11/10/17.
 */
trait SimpleArtifactPersistenceDao {

  def namespace: Namespace

  def create[T: SerializationSpecifier](obj: T): Future[Id[T]]

  def createOrUpdate[T: SerializationSpecifier](obj: T): Future[Id[T]]

  def read[T: SerializationSpecifier](id: Id[T]): Future[T]

  def readIfAvailable[T: SerializationSpecifier](id: Id[T]): Future[Option[T]]

  def update[T: SerializationSpecifier](id: Id[T], udateFunction: T ⇒ T): Future[Unit]

  def deleteObject[T: SerializationSpecifier](id: Id[T]): Future[Unit]

  def getAll[T: SerializationSpecifier](fromAndSize: Option[(Int, Int)] = None): Future[SearchResponse[T]]

  // These methods MUST NOT be called from anywhere other than test classes. The private[persistence] method protects against external access
  private[persistence] def afterTestCleanup(): Unit

  def init(): Future[Unit]

  def info: Option[PersistenceInfo]

  /*
  * This functions is called when updating a gateway object. Since gateways are also maintained as nested objects inside deployment -> clusters-> gateway,
  * consistency with these nested objects must be maintained.
  * */
  def updateGatewayForDeployment(gateway: Gateway)(implicit sSpecifier: SerializationSpecifier[Deployment], ec: ExecutionContext): Future[Unit] = {
    for {
      deployments <- getAll[Deployment]().map(_.response)
      deploymentsThatNeedUpdate = deployments.filter(_.clusters.exists(_.gateways.exists(_.name == gateway.name)))
      _ <- Future.sequence(deploymentsThatNeedUpdate.map(deployment => update[Deployment](sSpecifier.idExtractor(deployment),
        d => d.copy(clusters = d.clusters.map(c => c.copy(gateways = c.gateways.map(g => if(g.name == gateway.name) gateway else g))))
      )))
    } yield ()
  }
}

case class PersistenceInfo(info: String, database: PersistenceDatabase)

case class PersistenceDatabase(`type`: String, connection: String)

case class SearchResponse[T](response: List[T], from: Int, size: Int, total: Int)

trait SimpleArtifactPersistenceDaoFactory {
  def get(namespace: Namespace, actorSystem: ActorSystem): SimpleArtifactPersistenceDao
}
