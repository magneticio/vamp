package io.vamp.core.persistence.store

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.{ExecutionContextProvider, FutureSupport}
import io.vamp.common.http.{OffsetEnvelope, RestClient}
import io.vamp.core.model.artifact._
import io.vamp.core.model.serialization._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import org.json4s.native.Serialization.write
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

case class ElasticsearchStoreInfo(`type`: String, elasticsearch: Any)

class ElasticsearchStoreProvider(ec: ExecutionContext) extends StoreProvider with FutureSupport with PersistenceNotificationProvider {

  private val logger = Logger(LoggerFactory.getLogger(classOf[ElasticsearchStoreProvider]))

  val store: Store = new ElasticsearchStore()

  private class ElasticsearchStore extends Store with ExecutionContextProvider {

    private val indexPrefix = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.index.prefix")
    private val elasticsearchUrl: String = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.url")

    implicit val executionContext = ec
    implicit val formats = CoreSerializationFormat.default
    implicit val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.persistence.elasticsearch.response-timeout").seconds)

    def info = ElasticsearchStoreInfo("elasticsearch", offload(RestClient.request[Any](s"GET $elasticsearchUrl")))

    def all(`type`: Class[_ <: Artifact]): List[Artifact] = {
      logger.trace(s"persistence elasticsearch all [${`type`.getSimpleName}]")
      Nil
    }

    def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
      val (p, pp) = OffsetEnvelope.normalize(0, page, perPage, 30)
      ArtifactResponseEnvelope(Nil, 0, p, pp)
    }

    def create(artifact: Artifact, ignoreIfExists: Boolean = true): Artifact = {
      logger.trace(s"persistence elasticsearch create [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
      artifact
    }

    def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = {
      logger.trace(s"persistence elasticsearch read [${`type`.getSimpleName}] - $name}")
      None
    }

    def update(artifact: Artifact, create: Boolean = false): Artifact = {
      logger.trace(s"persistence elasticsearch update [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
      artifact
    }

    def delete(name: String, `type`: Class[_ <: Artifact]): Artifact = {
      logger.trace(s"persistence elasticsearch delete [${`type`.getSimpleName}] - $name}")
      error(ArtifactNotFound(name, `type`))
    }
  }

  private def typeOf(`type`: Class[_ <: Artifact]): String = `type` match {
    case t if classOf[Deployment].isAssignableFrom(t) => "deployments"
    case t if classOf[Breed].isAssignableFrom(t) => "breeds"
    case t if classOf[Blueprint].isAssignableFrom(t) => "blueprints"
    case t if classOf[Sla].isAssignableFrom(t) => "slas"
    case t if classOf[Scale].isAssignableFrom(t) => "scales"
    case t if classOf[Escalation].isAssignableFrom(t) => "escalations"
    case t if classOf[Routing].isAssignableFrom(t) => "routings"
    case t if classOf[Filter].isAssignableFrom(t) => "filters"
    case request => error(UnsupportedPersistenceRequest(`type`))
  }
}
