package io.vamp.core.persistence.store

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.{ExecutionContextProvider, FutureSupport}
import io.vamp.common.http.{OffsetEnvelope, RestClient}
import io.vamp.core.model.artifact._
import io.vamp.core.model.reader._
import io.vamp.core.model.serialization._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider}
import org.json4s.native.Serialization.write
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

case class ElasticsearchStoreInfo(`type`: String, elasticsearch: Any)

case class ElasticsearchArtifact(name: String, artifact: String)

case class ElasticsearchSearchResponse(hits: ElasticsearchSearchHits)

case class ElasticsearchSearchHits(total: Long, hits: List[Map[String, _]])

class ElasticsearchStore(ec: ExecutionContext) extends Store with TypeOfArtifact with FutureSupport with ExecutionContextProvider with PersistenceNotificationProvider {

  private val logger = Logger(LoggerFactory.getLogger(classOf[ElasticsearchStore]))

  private val index = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.index")
  private val elasticsearchUrl: String = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.url")

  implicit val executionContext = ec
  implicit val formats = CoreSerializationFormat.full
  implicit val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.persistence.elasticsearch.response-timeout").seconds)

  private val readers: Map[String, YamlReader[_ <: Artifact]] = Map(
    "deployments" -> DeploymentReader,
    "breeds" -> BreedReader,
    "blueprints" -> BlueprintReader,
    "slas" -> SlaReader,
    "scales" -> ScaleReader,
    "escalations" -> EscalationReader,
    "routings" -> RoutingReader,
    "filters" -> FilterReader,
    "workflows" -> WorkflowReader,
    "scheduled-workflows" -> ScheduledWorkflowReader
  )

  def info = ElasticsearchStoreInfo("elasticsearch", offload(RestClient.request[Any](s"GET $elasticsearchUrl")))

  def all(`type`: Class[_ <: Artifact]): List[Artifact] = {
    logger.trace(s"Elasticsearch persistence: all [${`type`.getSimpleName}]")
    // TODO iterate properly - this is related to pagination in general, we should use streams or something similar.
    val perPage = ArtifactResponseEnvelope.maxPerPage
    val (total, artifacts) = findAllArtifactsBy(`type`, 0, perPage)
    if (total > artifacts.size)
      (1 until (total / perPage + (if (total % perPage == 0) 0 else 1)).toInt).foldRight(artifacts)((i, list) => list ++ findAllArtifactsBy(`type`, i * perPage, perPage)._2)
    else artifacts
  }

  def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
    val (total, artifacts) = findAllArtifactsBy(`type`, (p - 1) * pp, pp)
    val (rp, rpp) = OffsetEnvelope.normalize(total, p, pp, ArtifactResponseEnvelope.maxPerPage)
    ArtifactResponseEnvelope(artifacts, total, rp, rpp)
  }

  def create(artifact: Artifact, ignoreIfExists: Boolean = true): Artifact = {
    logger.trace(s"Elasticsearch persistence: create [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    artifact match {
      case blueprint: DefaultBlueprint => blueprint.clusters.flatMap(_.services).map(_.breed).foreach(breed => create(breed, ignoreIfExists = true))
      case _ =>
    }
    offload(RestClient.request[Any](s"POST $elasticsearchUrl/$index/${typeOf(artifact.getClass)}", ElasticsearchArtifact(artifact.name, write(artifact))))
    artifact
  }

  def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = {
    logger.trace(s"Elasticsearch persistence: read [${`type`.getSimpleName}] - $name}")
    findHitBy(name, `type`) match {
      case None => None
      case Some(hit) => deserialize(`type`, hit)
    }
  }

  def update(artifact: Artifact, create: Boolean = false): Artifact = {
    logger.trace(s"Elasticsearch persistence: update [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    findHitBy(artifact.name, artifact.getClass) match {
      case None => if (create) this.create(artifact) else error(ArtifactNotFound(artifact.name, artifact.getClass))
      case Some(hit) =>
        // TODO validate response
        hit.get("Id").foreach(id => RestClient.request[Any](s"POST $elasticsearchUrl/$index/${typeOf(artifact.getClass)}/$id", ElasticsearchArtifact(artifact.name, write(artifact))))
    }
    artifact
  }

  def delete(name: String, `type`: Class[_ <: Artifact]): Artifact = {
    logger.trace(s"Elasticsearch persistence: delete [${`type`.getSimpleName}] - $name}")
    findHitBy(name, `type`) match {
      case None => error(ArtifactNotFound(name, `type`))
      case Some(hit) => deserialize(`type`, hit) match {
        case None => error(ArtifactNotFound(name, `type`))
        case Some(artifact) =>
          // TODO validate response
          hit.get("Id").foreach(id => RestClient.delete(s"$elasticsearchUrl/$index/${typeOf(artifact.getClass)}/$id"))
          artifact
      }
    }
  }

  private def findAllArtifactsBy(`type`: Class[_ <: Artifact], from: Int, size: Int): (Long, List[Artifact]) = {
    offload(RestClient.request[ElasticsearchSearchResponse](s"GET $elasticsearchUrl/$index/${typeOf(`type`)}/_search", Map("from" -> from, "size" -> size)).map { response =>
      response.hits.total -> response.hits.hits.flatMap(hit => deserialize(`type`, hit))
    }) match {
      case (total: Long, artifacts: List[_]) => total -> artifacts.asInstanceOf[List[Artifact]]
      case e: Exception =>
        logger.error(e.getMessage, e)
        throw e
    }
  }

  private def findHitBy(name: String, `type`: Class[_ <: Artifact]): Option[Map[String, _]] = {
    offload(RestClient.request[ElasticsearchSearchResponse](s"GET $elasticsearchUrl/$index/${typeOf(`type`)}/_search",
      Map("from" -> 0, "size" -> 1, "query" -> ("term" -> ("name" -> name)))).map { response =>
      if (response.hits.total == 1) Some(response.hits.hits.head) else None
    }) match {
      case r: Option[_] => r.asInstanceOf[Option[Map[String, _]]]
      case e: Exception =>
        logger.error(e.getMessage, e)
        throw e
    }
  }

  private def deserialize(`type`: Class[_ <: Artifact], hit: Map[String, _]): Option[Artifact] = {
    hit.get("Source").flatMap(_.asInstanceOf[Map[String, _]].get("artifact")).flatMap { artifact =>
      readers.get(typeOf(`type`)).flatMap(reader => Some(reader.read(artifact.toString)))
    }
  }
}
