package io.vamp.persistence.refactor

import io.vamp.model.artifact.EnvironmentVariable
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}

/**
  * Created by mihai on 11/10/17.
  */
class SimpleESPersistenceTest extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  behavior of "EsDao"
  it should "Correctly Persist Artifacts" in { persistence =>
    persistence.persistenceDao.create(EnvironmentVariable(name = "SimpleEnvVar", alias = None,
      value = None, interpolated = None))
  }

  it should "Correctly Persist Artifacts2" in { persistence =>
    persistence.persistenceDao.create(EnvironmentVariable(name = "SimpleEnvVar", alias = None,
      value = None, interpolated = None))
  }

}
