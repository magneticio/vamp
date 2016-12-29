package io.vamp.persistence.db

import io.vamp.common.config.Config
import io.vamp.common.spi.ClassMapper
import io.vamp.model.artifact._
import io.vamp.pulse.ElasticsearchClient
import io.vamp.pulse.ElasticsearchClient.{ ElasticsearchGetResponse, ElasticsearchSearchResponse }

import scala.concurrent.Future

class ElasticsearchPersistenceActorMapper extends ClassMapper {
  val name = "elasticsearch"
  val clazz = classOf[ElasticsearchPersistenceActor]
}

object ElasticsearchPersistenceActor {
  lazy val index = Config.string("vamp.persistence.database.elasticsearch.index")
  lazy val elasticsearchUrl: String = Config.string("vamp.persistence.database.elasticsearch.url")
}

case class ElasticsearchArtifact(artifact: String)

case class ElasticsearchPersistenceInfo(`type`: String, url: String, index: String, initializationTime: String, elasticsearch: Any)

class ElasticsearchPersistenceActor extends PersistenceActor with PersistenceMarshaller with TypeOfArtifact with PaginationSupport {

  import ElasticsearchPersistenceActor._

  private val es = new ElasticsearchClient(elasticsearchUrl)

  protected def info(): Future[Any] = for {
    health ← es.health
    initializationTime ← es.creationTime(index)
  } yield ElasticsearchPersistenceInfo("elasticsearch", elasticsearchUrl, index, initializationTime, health)

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope] = {
    log.debug(s"${getClass.getSimpleName}: all [${type2string(`type`)}] of $page per $perPage")

    val from = (page - 1) * perPage
    es.search[ElasticsearchSearchResponse](index, `type`,
      s"""
         |{
         |  "query": {
         |    "filtered": {
         |      "query": {
         |        "match_all": {}
         |      }
         |    }
         |  },
         |  "from": $from,
         |  "size": $perPage
         |}
        """.stripMargin) map {
      response ⇒ ArtifactResponseEnvelope(response.hits.hits.flatMap { hit ⇒ read(`type`, hit._source) }, response.hits.total, from, perPage)
    }
  }

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]] = {
    log.debug(s"${getClass.getSimpleName}: read [${type2string(`type`)}] - $name}")
    es.get[ElasticsearchGetResponse](index, `type`, name) map {
      hit ⇒ if (hit.found) read(`type`, hit._source) else None
    }
  }

  protected def set(artifact: Artifact): Future[Artifact] = {
    val json = marshall(artifact)
    log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - $json")
    es.index[Any](index, artifact.getClass, artifact.name, ElasticsearchArtifact(json)).flatMap(_ ⇒ es.refresh(index)).map(_ ⇒ artifact)
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean] = {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    es.delete(index, `type`, name).flatMap(r ⇒ es.refresh(index).map(_ ⇒ r != None))
  }

  private def read(`type`: String, source: Map[String, Any]): Option[Artifact] = {
    source.get("artifact").flatMap(artifact ⇒ unmarshall(`type`, artifact.toString))
  }
}
