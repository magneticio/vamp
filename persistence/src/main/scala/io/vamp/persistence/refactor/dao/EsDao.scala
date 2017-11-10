package io.vamp.persistence.refactor.dao

import akka.event.slf4j.Logger
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri, IndexAndType, TcpClient}
import io.vamp.common.{Config, Id, Namespace}
import io.vamp.persistence.refactor.api.SimpleArtifactPersistenceDao
import io.vamp.persistence.refactor.serialization.SerializationSpecifier
import spray.json.RootJsonFormat
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.common.settings.Settings

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import spray.json._

/**
  * Created by mihai on 11/10/17.
  */
class EsDao(val namespace: Namespace, elasticSearchHostAndPort: String, elasticSearchClusterName: String)(implicit ec: ExecutionContext) extends SimpleArtifactPersistenceDao {
  implicit val ns: Namespace = namespace
  val indexName = s"vamp_${namespace.name}"

  lazy val esClient : TcpClient = {
    val esClientUri: ElasticsearchClientUri = ElasticsearchClientUri(elasticSearchHostAndPort)
    val settings: Settings = Settings.builder()
      .put("cluster.name", elasticSearchClusterName)
      .build()
    println("Creating TCPClient")
    val client: TcpClient = TcpClient.transport(settings, esClientUri)
    println("Finished Creating TCPClient")
    Await.result(
      (for {
        indexExists <- client.execute(indexExists(indexName))
        _ <- if(indexExists.isExists) {
          // Do nothing; This index is already created
          // Must log here
          Future.successful(() )
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
      if(!getResponse.exists || getResponse.isSourceEmpty) throw new RuntimeException(s"Not Found Object with Id ${objectId} of type ${s.typeName}")
      else getResponse.sourceAsString.parseJson.convertTo[T]
    }
  }

  override def update[T](obj: T)(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[Unit] = {
    val newObjectId = s.idExtractor(obj)
    for {
      _ <- esClient.execute {
        (indexInto(indexName, s.typeName) doc (obj.toJson.toString()) id (newObjectId)).copy(createOnly = Some(false))
      }
    } yield ()
  }

  override def deleteObject[T](objectId: Id[T])(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[Unit] = {
    for {
      _ <- esClient.execute {
        delete (objectId.value) from(IndexAndType(indexName, s.typeName))
      }
    } yield ()
  }

  private[persistence] def afterTestCleanup: Unit = Await.result(esClient.execute(deleteIndex(indexName)), 10.second)
}
