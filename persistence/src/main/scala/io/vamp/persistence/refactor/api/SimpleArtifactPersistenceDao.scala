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

  def getAll[T](s: SerializationSpecifier[T], fromAndSize: Option[(Int, Int)] = None): Future[List[T]]

  // These methods MUST NOT be called from anywhere other than test classes. The private[persistence] method protects against external access
  private[persistence] def afterTestCleanup(): Unit
  private[persistence] val indexName: String
}

trait SimpleArtifactPersistenceDaoFactory {
  def get(namespace: Namespace): SimpleArtifactPersistenceDao
}
