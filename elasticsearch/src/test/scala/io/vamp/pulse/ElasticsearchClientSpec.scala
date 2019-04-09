package io.vamp.pulse

import java.nio.file._
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.Timeout
import com.sksamuel.elastic4s.embedded.LocalNode
import io.vamp.common.{ Config, Namespace }
import org.scalatest.{ BeforeAndAfter, FunSpec, Matchers }

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ElasticsearchClientSpec extends FunSpec with BeforeAndAfter with Matchers {
  private implicit val namespace: Namespace = Namespace("default")
  private implicit val actorSystem: ActorSystem = ActorSystem("RouteComparatorSpec")
  private implicit val timeout: Timeout = new Timeout(10, TimeUnit.SECONDS)

  private val clusterName: String = "elasticsearch-local-client"
  private val homePath: Path = Files.createTempDirectory(clusterName)
  private val localNode = LocalNode(clusterName, homePath.toAbsolutePath.toString)
  private val elasticSearchHttpClient = localNode.client(true)
  private val tenSecondsTimeout = Duration(10, TimeUnit.SECONDS)

  describe("An ElasticSearchClient") {

    Config.load(Map("vamp.common.http.client.tls-check" -> false))
    val elasticSearchClient = new ElasticsearchClient(elasticSearchHttpClient)

    describe("when getting elasticsearch version") {
      val version = Await.result(elasticSearchClient.version(), tenSecondsTimeout)
      it("should return master node version") {
        version should be(Some("6.5.2"))
      }
    }

    describe("when getting health") {
      val health = Await.result(elasticSearchClient.health(), tenSecondsTimeout).toString
      it("should return health information") {
        health should not be "Could not get cluster health information"
      }
    }
  }

}
