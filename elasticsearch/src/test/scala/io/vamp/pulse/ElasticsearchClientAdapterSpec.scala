package io.vamp.pulse

import java.nio.file._
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.sksamuel.elastic4s.embedded.LocalNode
import com.sksamuel.elastic4s.http.ElasticDsl._
import io.vamp.common.{ Config, Namespace }
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

class ElasticsearchClientAdapterSpec extends TestKit(ActorSystem("ElasticsearchClientAdapterSpec")) with FunSpecLike with BeforeAndAfterAll with Matchers {
  private implicit val namespace: Namespace = Namespace("default")
  private implicit val timeout: Timeout = new Timeout(10, TimeUnit.SECONDS)

  private val clusterName: String = "elasticsearch-local-client"
  private val homePath: Path = Files.createTempDirectory(clusterName)
  private val localNode = LocalNode(clusterName, homePath.toAbsolutePath.toString)
  private val elasticSearchHttpClient = localNode.client(true)
  private val tenSecondsTimeout = Duration(10, TimeUnit.SECONDS)

  override def afterAll {
    elasticSearchHttpClient.close()
    shutdown()
  }

  describe("ElasticSearchClient") {

    Config.load(Map("vamp.common.http.client.tls-check" → false))
    val elasticSearchClient = new ElasticsearchClientAdapter(elasticSearchHttpClient)

    Try {
      elasticSearchHttpClient.execute {
        deleteIndex("countries")
      }.await
    }

    elasticSearchHttpClient.execute {
      createIndex("countries").mappings(
        mapping("data").fields(
          textField("country").fielddata(true),
          textField("capital").fielddata(true),
          intField("citizens").stored(true),
          dateField("timestamp")
        )
      )
    }.await

    elasticSearchHttpClient.execute {
      bulk(
        indexInto("countries" / "data").fields("country" → "Mongolia", "capital" → "Ulaanbaatar", "citizens" → 1418000, "timestamp" → "2019-04-01T12:10:30Z") id "1",
        indexInto("countries" / "data").fields("country" → "Poland", "capital" → "Warsaw", "citizens" → 1765000, "timestamp" → "2020-04-01T12:10:30Z") id "2",
        indexInto("countries" / "data").fields("country" → "Portugal", "capital" → "Lisbon", "citizens" → 504718, "timestamp" → "2021-04-01T12:10:30Z") id "3"
      ).refreshImmediately
    }.await

    describe("when getting elasticsearch version") {
      val version = Await.result(elasticSearchClient.version(), tenSecondsTimeout)

      it("should return master node version") {
        version should be(Some("6.5.2"))
      }
    }

    describe("when getting health") {
      val health = Await.result(elasticSearchClient.health(), tenSecondsTimeout).toString

      it("should return health information") {
        health should not startWith "Could not get health:"
      }
    }

    describe("when getting count") {
      describe("for index with two entries") {

        describe("using match all query") {
          val countResponse = Await.result(elasticSearchClient.count("countries", matchAllQuery()), tenSecondsTimeout)

          it("should return all entries") {
            countResponse.count should be(3L)
          }
        }

        describe("using raw query matching only one element") {
          val queryJson = "{\"prefix\": { \"country\": \"pola\" } }"
          val countResponse = Await.result(elasticSearchClient.count("countries", rawQuery(queryJson)), tenSecondsTimeout)

          it("should return 1") {
            countResponse.count should be(1L)
          }
        }
      }
    }

    describe("when checking if document exists") {

      describe("for existing document") {
        val existsResponse = Await.result(elasticSearchClient.exists("countries", "data", "1"), tenSecondsTimeout)

        it("should return true") {
          existsResponse should be(true)
        }
      }

      describe("for non-existent document") {
        val existsResponse = Await.result(elasticSearchClient.exists("countries", "data", "99"), tenSecondsTimeout)

        it("should return false") {
          existsResponse should be(false)
        }
      }
    }

    describe("when searching for documents") {
      describe("and documents exist") {
        val queryJson = "{\"prefix\": { \"country\": \"po\" } }"
        val sort = fieldSort("timestamp").desc()
        val searchResponse = Await.result(elasticSearchClient.search("countries", rawQuery(queryJson), 0, 10, sort), tenSecondsTimeout)

        it("should return existing documents") {
          searchResponse.hits.total should be(2L)
          searchResponse.hits.hits(0)._id should be("3")
          searchResponse.hits.hits(1)._id should be("2")
        }
      }

      describe("and document does not exist") {
        val queryJson = "{\"prefix\" : { \"country\" : \"denmark\" } }"
        val sort = fieldSort("capital")
        val searchResponse = Await.result(elasticSearchClient.search("countries", rawQuery(queryJson), 1, 10, sort), tenSecondsTimeout)

        it("should not return any documents") {
          searchResponse.hits.total should be(0L)
        }
      }
    }

    describe("when indexing document") {
      val doc = "{ \"country\": \"Spain\", \"capital\": \"Madrid\", \"timestamp\": \"2022-04-01T12:10:30Z\" }"
      val indexResponse = Await.result(elasticSearchClient.index("countries", "data", doc), tenSecondsTimeout)

      it("document should be stored") {
        indexResponse._id should not be empty
      }
    }

    describe("when getting aggregation") {
      val queryJson = "{\"prefix\": { \"country\": \"po\" } }"
      val aggregation = avgAgg("citizensAvg", "citizens")
      val aggregationResponse = Await.result(elasticSearchClient.aggregate("countries", rawQuery(queryJson), aggregation), tenSecondsTimeout)

      it("should return aggregation value") {
        aggregationResponse.aggregations.aggregation.value should be(1134859.0)
      }
    }

    describe("when creating index template") {
      val rawMapping = """{
                          "_all": {
                              "enabled": false
                              },
                          "properties": {
                              "tags": {
                                  "type": "keyword",
                                  "index": true
                              },
                              "timestamp": {
                                  "type": "date",
                                  "format": "date_optional_time"
                              },
                              "type": {
                                  "type": "keyword",
                                  "index": true
                              }
                          }
                      }"""
      val pattern = "countries-*"
      val mappings = Seq(mapping("testMapping").rawSource(rawMapping))
      val createTemplateResponse = Await.result(elasticSearchClient.createIndexTemplate(name = "countries", pattern = pattern, order = 1, mappings = mappings), tenSecondsTimeout)

      it("should acknowledge that template is created") {
        createTemplateResponse.acknowledged should be(true)
      }

      describe("and checking if template exists") {

        describe("for existing template") {
          val templateExistsResponse = Await.result(elasticSearchClient.templateExists("countries"), tenSecondsTimeout)

          it("should return true") {
            templateExistsResponse should be(true)
          }
        }

        describe("for non-existent template") {
          val templateExistsResponse = Await.result(elasticSearchClient.templateExists("cars"), tenSecondsTimeout)

          it("should return false") {
            templateExistsResponse should be(false)
          }
        }
      }
    }
  }
}