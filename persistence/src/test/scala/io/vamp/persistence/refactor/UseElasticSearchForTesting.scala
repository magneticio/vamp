package io.vamp.persistence.refactor

import io.vamp.common.{Config, Namespace}
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

/**
  * Created by mihai on 11/10/17.
  *
  *
  * Simplest way of running ElasticSearch for local tests is:
  * docker container run --name=elastic_runner -p 9200:9200 -p 9300:9300 -e "xpack.security.enabled=false" -e "discovery.type=single-node" -d docker.elastic.co/elasticsearch/elasticsearch:5.6.4
  *
  * Simplest way of running Kibana and debugging should tests go wrong:
  * docker run --name some-kibana -e ELASTICSEARCH_URL=http://<YOUR IP>:9200 -p 5601:5601 docker.elastic.co/kibana/kibana:5.6.4
  *
  */
trait UseElasticSearchForTesting {
  this: fixture.FlatSpec =>

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  type FixtureParam = Namespace

  def simpleAwait[T](f: => Future[T]) = Await.result(f, 10.seconds)

  override protected def withFixture(test: OneArgTest): Outcome = {
    implicit val ns: Namespace = Namespace(sanitizeName(test.name))
    val outcome = Try {

      val persistenceConfig: Map[String, Map[String, Any]] = Map("vamp.persistence.database" → Map(
        "type" → "elasticsearch",
        "elasticsearch.elasticsearch-url" → "elasticsearch://localhost:9300",
        "elasticsearch.elasticsearch-cluster-name" → "docker-cluster",
        "elasticsearch.elasticsearch-test-cluster" → true
      ))
      Config.load(Config.unmarshall(Config.marshall(persistenceConfig), flatten = true))
      val bb = Config.export(Config.Type.dynamic, flatten = true)
      test(ns)
    }

    outcome match {
      case Success(Succeeded) => {
        println(s"Test for namespace ${ns.name} Succesful. Deleting index")
        VampPersistence.persistenceDao.afterTestCleanup
      }
      case _ => {// Do nothing
        println(s"Test for index ${ns.name} FAILED. Keeping index in order to debug")
      }
    }
    outcome.get
  }

  private def sanitizeName(testName: String) =
    testName.toLowerCase.trim.replace(" ", "").takeRight(128)

}
