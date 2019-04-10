package io.vamp.pulse

import java.nio.file._
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.Timeout
import com.sksamuel.elastic4s.embedded.LocalNode
import com.sksamuel.elastic4s.http.ElasticDsl._
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

  describe("ElasticSearchClient") {

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

    describe("when getting count") {
      describe("for index with two entries") {

        elasticSearchHttpClient.execute {
          bulk(
            indexInto("countries" / "data").fields("country" -> "Mongolia", "capital" -> "Ulaanbaatar"),
            indexInto("countries" / "data").fields("country" -> "Poland", "capital" -> "Warsaw")
          ).refreshImmediately
        }.await

        describe("using match all query") {
          val countResponse = Await.result(elasticSearchClient.count("countries", matchAllQuery()), tenSecondsTimeout)

          it("should return 2") {
            countResponse.count should be(2L)
          }
        }

        describe("using raw query matching only one element") {
          val queryJson = "{\"prefix\" : { \"country\" : \"pola\" } }"
          val countResponse = Await.result(elasticSearchClient.count("countries", rawQuery(queryJson)), tenSecondsTimeout)

          it("should return 1") {
            countResponse.count should be(1L)
          }
        }
      }
    }

    describe("when checking if document exists") {

      elasticSearchHttpClient.execute {
        bulk(
          indexInto("countries" / "data").fields("country" -> "Mongolia", "capital" -> "Ulaanbaatar") id "1",
          indexInto("countries" / "data").fields("country" -> "Poland", "capital" -> "Warsaw") id "2"
        ).refreshImmediately
      }.await

      describe("for existing document") {
        val existsResponse = Await.result(elasticSearchClient.exists("countries", "data", "1"), tenSecondsTimeout)

        it("should return true") {
          existsResponse should be(true)
        }
      }
      describe("for non-existent document") {
        val existsResponse = Await.result(elasticSearchClient.exists("countries", "data", "3"), tenSecondsTimeout)

        it("should return false") {
          existsResponse should be(false)
        }
      }
    }
  }

}
