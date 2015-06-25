package io.vamp.core.persistence

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.ActorDescription
import io.vamp.common.http.{OffsetEnvelope, RestClient}
import io.vamp.core.model.artifact.{Artifact, DefaultBlueprint}
import io.vamp.core.model.reader._
import io.vamp.core.model.serialization.CoreSerializationFormat
import io.vamp.core.persistence.notification.{ArtifactAlreadyExists, ArtifactNotFound}
import org.json4s.native.Serialization._

object ElasticsearchPersistenceActor extends ActorDescription {

  def props(args: Any*): Props = Props(classOf[ElasticsearchPersistenceActor], args: _*)

  lazy val index = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.index")

  lazy val elasticsearchUrl: String = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.url")
}

case class ElasticsearchPersistenceInfo(`type`: String, elasticsearch: Any)

case class ElasticsearchArtifact(name: String, artifact: String)

case class ElasticsearchSearchResponse(hits: ElasticsearchSearchHits)

case class ElasticsearchSearchHits(total: Long, hits: List[Map[String, _]])

class ElasticsearchPersistenceActor extends PersistenceActor with TypeOfArtifact {

  import ElasticsearchPersistenceActor._

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

  protected def info() = ElasticsearchPersistenceInfo("elasticsearch", offload(RestClient.get[Any](s"$elasticsearchUrl")))

  protected def all(`type`: Class[_ <: Artifact]): List[Artifact] = {
    log.debug(s"Elasticsearch persistence: all [${`type`.getSimpleName}]")
    // TODO iterate properly - this is related to pagination in general, we should use streams or something similar.
    val perPage = ArtifactResponseEnvelope.maxPerPage
    val (total, artifacts) = findAllArtifactsBy(`type`, 0, perPage)
    if (total > artifacts.size)
      (1 until (total / perPage + (if (total % perPage == 0) 0 else 1)).toInt).foldRight(artifacts)((i, list) => list ++ findAllArtifactsBy(`type`, i * perPage, perPage)._2)
    else artifacts
  }

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
    val (total, artifacts) = findAllArtifactsBy(`type`, (p - 1) * pp, pp)
    val (rp, rpp) = OffsetEnvelope.normalize(total, p, pp, ArtifactResponseEnvelope.maxPerPage)
    ArtifactResponseEnvelope(artifacts, total, rp, rpp)
  }

  protected def create(artifact: Artifact, ignoreIfExists: Boolean = false): Artifact = {
    implicit val formats = CoreSerializationFormat.full

    log.debug(s"Elasticsearch persistence: create [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    artifact match {
      case blueprint: DefaultBlueprint => blueprint.clusters.flatMap(_.services).map(_.breed).foreach(breed => create(breed, ignoreIfExists = true))
      case _ =>
    }
    val artifacts = typeOf(artifact.getClass)
    findHitBy(artifact.name, artifact.getClass) match {
      case None =>
        // TODO validate response
        offload(RestClient.post[Any](s"$elasticsearchUrl/$index/$artifacts", ElasticsearchArtifact(artifact.name, write(artifact))))
      case Some(hit) =>
        if (!ignoreIfExists) error(ArtifactAlreadyExists(artifact.name, artifact.getClass))
        // TODO validate response
        hit.get("_id").foreach(id => offload(RestClient.post[Any](s"$elasticsearchUrl/$index/$artifacts/$id", ElasticsearchArtifact(artifact.name, write(artifact)))))
    }
    artifact
  }

  protected def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = {
    log.debug(s"Elasticsearch persistence: read [${`type`.getSimpleName}] - $name}")
    findHitBy(name, `type`) match {
      case None => None
      case Some(hit) => deserialize(`type`, hit)
    }
  }

  protected def update(artifact: Artifact, create: Boolean = false): Artifact = {
    implicit val formats = CoreSerializationFormat.full

    log.debug(s"Elasticsearch persistence: update [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    findHitBy(artifact.name, artifact.getClass) match {
      case None => if (create) this.create(artifact) else error(ArtifactNotFound(artifact.name, artifact.getClass))
      case Some(hit) =>
        // TODO validate response
        hit.get("_id").foreach(id => offload(RestClient.post[Any](s"$elasticsearchUrl/$index/${typeOf(artifact.getClass)}/$id", ElasticsearchArtifact(artifact.name, write(artifact)))))
    }
    artifact
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Artifact = {
    log.debug(s"Elasticsearch persistence: delete [${`type`.getSimpleName}] - $name}")
    findHitBy(name, `type`) match {
      case None => error(ArtifactNotFound(name, `type`))
      case Some(hit) => deserialize(`type`, hit) match {
        case None => error(ArtifactNotFound(name, `type`))
        case Some(artifact) =>
          // TODO validate response
          hit.get("_id").foreach(id => offload(RestClient.delete(s"$elasticsearchUrl/$index/${typeOf(artifact.getClass)}/$id")))
          artifact
      }
    }
  }

  private def findAllArtifactsBy(`type`: Class[_ <: Artifact], from: Int, size: Int): (Long, List[Artifact]) = {
    offload(RestClient.post[ElasticsearchSearchResponse](s"$elasticsearchUrl/$index/${typeOf(`type`)}/_search", Map("from" -> from, "size" -> size)).map {
      case None => 0L -> Nil
      case Some(response) => response.hits.total -> response.hits.hits.flatMap(hit => deserialize(`type`, hit))
    }) match {
      case (total: Long, artifacts: List[_]) => total -> artifacts.asInstanceOf[List[Artifact]]
      case e: Exception =>
        log.error(e.getMessage, e)
        throw e
    }
  }

  private def findHitBy(name: String, `type`: Class[_ <: Artifact]): Option[Map[String, _]] = {
    offload(RestClient.post[ElasticsearchSearchResponse](s"$elasticsearchUrl/$index/${typeOf(`type`)}/_search",
      Map("from" -> 0, "size" -> 1, "query" -> ("term" -> ("name" -> name)))).map {
      case None => None
      case Some(response) => if (response.hits.total == 1) Some(response.hits.hits.head) else None
    }) match {
      case r: Option[_] => r.asInstanceOf[Option[Map[String, _]]]
      case e: Exception =>
        log.error(e.getMessage, e)
        throw e
    }
  }

  private def deserialize(`type`: Class[_ <: Artifact], hit: Map[String, _]): Option[Artifact] = {
    hit.get("_source").flatMap(_.asInstanceOf[Map[String, _]].get("artifact")).flatMap { artifact =>
      readers.get(typeOf(`type`)).flatMap(reader => Some(reader.read(artifact.toString)))
    }
  }
}
