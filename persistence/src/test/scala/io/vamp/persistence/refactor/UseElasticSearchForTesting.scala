package io.vamp.persistence.refactor

import io.vamp.common.Namespace
import io.vamp.persistence.refactor.api.SimpleArtifactPersistenceDao
import io.vamp.persistence.refactor.dao.EsDao
import org.scalatest._

import scala.concurrent.ExecutionContext
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
  type FixtureParam = VampPersistenceAssembly

  override protected def withFixture(test: OneArgTest): Outcome = {
    var fixtureParam :Option[VampPersistenceAssembly] = None
    val outcome = Try {
      fixtureParam = Some(new VampPersistenceAssembly {
        override def persistenceDao: SimpleArtifactPersistenceDao = new EsDao(namespace = Namespace(sanitizeName(test.name)),
        "elasticsearch://localhost:9300", "docker-cluster")
      })
      test(fixtureParam.get)
    }

    outcome match {
      case Success(_) if (fixtureParam.isDefined) => fixtureParam.get.persistenceDao.afterTestCleanup
      case _ => () // Do nothing
    }
    outcome.get
  }

  private def sanitizeName(testName: String) =
    testName.toLowerCase.trim.replace(" ", ".").takeRight(128)

}
