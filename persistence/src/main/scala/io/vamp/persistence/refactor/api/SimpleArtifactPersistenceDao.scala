package io.vamp.persistence.refactor.api

import io.vamp.common.{ Id, Namespace }
import io.vamp.persistence.refactor.serialization.SerializationSpecifier

import scala.concurrent.Future

/**
 * Created by mihai on 11/10/17.
 */
trait SimpleArtifactPersistenceDao {

  def namespace: Namespace

  def create[T: SerializationSpecifier](obj: T): Future[Id[T]]

  def read[T: SerializationSpecifier](id: Id[T]): Future[T]

  def update[T: SerializationSpecifier](id: Id[T], udateFunction: T ⇒ T): Future[Unit]

  def deleteObject[T: SerializationSpecifier](id: Id[T]): Future[Unit]

  def getAll[T:SerializationSpecifier](fromAndSize: Option[(Int, Int)] = None): Future[SearchResponse[T]]

  // These methods MUST NOT be called from anywhere other than test classes. The private[persistence] method protects against external access
  private[persistence] def afterTestCleanup(): Unit
  private[persistence] val indexName: String

  def info: String
}

case class SearchResponse[T](response: List[T], from: Int, size: Int, total: Int)

trait SimpleArtifactPersistenceDaoFactory {
  def get(namespace: Namespace): SimpleArtifactPersistenceDao
}
