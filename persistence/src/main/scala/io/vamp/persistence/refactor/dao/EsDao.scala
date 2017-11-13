package io.vamp.persistence.refactor.dao

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType, TcpClient}
import io.vamp.common.{Id, Namespace}
import io.vamp.persistence.refactor.api.SimpleArtifactPersistenceDao
import io.vamp.persistence.refactor.exceptions.{DuplicateObjectIdException, InvalidObjectIdException, VampPersistenceModificationException}
import io.vamp.persistence.refactor.serialization.SerializationSpecifier
import org.elasticsearch.common.settings.Settings
import spray.json.{RootJsonFormat, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Created by mihai on 11/10/17.
  */
class EsDao(val namespace: Namespace, elasticSearchHostAndPort: String, elasticSearchClusterName: String, testingContext: Boolean = false)(implicit ec: ExecutionContext) extends SimpleArtifactPersistenceDao {
  implicit val ns: Namespace = namespace
  private[persistence] val indexName = s"vamp_${namespace.name}"

  lazy val esClient : TcpClient = {
    val esClientUri: ElasticsearchClientUri = ElasticsearchClientUri(elasticSearchHostAndPort)
    val settings: Settings = Settings.builder()
      .put("cluster.name", elasticSearchClusterName)
      .build()
    val client: TcpClient = TcpClient.transport(settings, esClientUri)
    Await.result(
      (for {
        indexExists <- client.execute(indexExists(indexName))
        _ <- if(indexExists.isExists) {
          if(testingContext) {
            (client.execute(deleteIndex(indexName))).flatMap(_ => client.execute(createIndex(indexName)))
          } else Future.successful()
        } else {
          // Create the index
          client.execute(createIndex(indexName))
        }
      } yield ()), 10.second
    )
    client
  }


  override def create[T](obj: T)(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[Id[T]] = {
    val newObjectId = s.idExtractor(obj)
    for {
      _ <- read(newObjectId).flatMap(_ => Future.failed(DuplicateObjectIdException(newObjectId))).recover {
        case e: InvalidObjectIdException[_] => ()
      }
      _ <- esClient.execute {
        (indexInto(indexName, s.typeName) doc (obj.toJson.toString()) id (newObjectId)).copy(createOnly = Some(true))
      }
    } yield newObjectId
  }


  override def read[T](objectId: Id[T])(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[T] = {
    for {
      getResponse <- esClient.execute {
        get(objectId.toString) from(indexName, s.typeName)
      }
    } yield {
      if(!getResponse.exists || getResponse.isSourceEmpty) throw new InvalidObjectIdException[T](objectId)
      else getResponse.sourceAsString.parseJson.convertTo[T]
    }
  }

  override def update[T](id: Id[T], updateFunction: T => T)(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[Unit] = {
    for {
      currentObject <- read(id)
      updatedObject = updateFunction(currentObject)
      _ <- if(s.idExtractor(updatedObject) != id) Future.failed(VampPersistenceModificationException(s"Changing id to ${s.idExtractor(updatedObject)}", id))
      else Future.successful()
      _ <- esClient.execute {
        (indexInto(indexName, s.typeName) doc (updatedObject.toJson.toString()) id (s.idExtractor(updatedObject))).copy(createOnly = Some(false))
      }
    } yield ()
  }

  override def deleteObject[T](objectId: Id[T])(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[Unit] = {
    for {
      _ <- read(objectId) // Ensure the object exists
      _ <- esClient.execute {
        delete (objectId.value) from(IndexAndType(indexName, s.typeName))
      }
    } yield ()
  }

  def getAll[T](s: SerializationSpecifier[T]): Future[List[T]] = {
    implicit val formatter: RootJsonFormat[T] = s.format
    for {
      numberOfObjects <- esClient.execute(search(indexName) types(s.typeName) size 0)
      allObjects <- esClient.execute(search(indexName) types(s.typeName) size numberOfObjects.totalHits.toInt)
    } yield {
      val responseHits = allObjects.original.getHits().getHits()
      responseHits.map(_.getSourceAsString.parseJson.convertTo[T]).toList
    }
  }

  private[persistence] def afterTestCleanup: Unit = Await.result(esClient.execute(deleteIndex(indexName)), 10.second)
}
