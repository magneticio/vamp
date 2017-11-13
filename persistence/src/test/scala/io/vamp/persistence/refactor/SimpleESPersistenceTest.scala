package io.vamp.persistence.refactor

import io.vamp.model.artifact.EnvironmentVariable
import io.vamp.persistence.refactor.exceptions.{DuplicateObjectIdException, InvalidObjectIdException, VampPersistenceModificationException}
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}
import io.vamp.common.{Id, Namespace}

import scala.concurrent.Future

/**
  * Created by mihai on 11/10/17.
  */
class SimpleESPersistenceTest extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  behavior of "EsDao"
  it should "Correctly Persist Artifacts" in { implicit namespace: Namespace =>
    val simpleEnvVar = EnvironmentVariable(name = "SimpleEnvVar", alias = Some("envVarAlias"),
      value = Some("evVarValue"), interpolated = Some("simpleInterpolatedValue"))

    // Create and retrieve; See that the object is there
    val envVarId = simpleAwait(VampPersistence.persistenceDao.create(simpleEnvVar))
    assert(simpleAwait(VampPersistence.persistenceDao.read(envVarId)) == simpleEnvVar)


    // Cannot create two objects with the same Id
    the[DuplicateObjectIdException[_]] thrownBy {
      simpleAwait(VampPersistence.persistenceDao.create(simpleEnvVar))
    }

    // Cannot modify the id of an object
    the[VampPersistenceModificationException[_]] thrownBy {
      simpleAwait(VampPersistence.persistenceDao.update(envVarId, ((e: EnvironmentVariable) => e.copy(name = "modifiedName"))))
    }

    val modifiedEnvVar = EnvironmentVariable(name = "SimpleEnvVar", alias = Some("modified_envVarAlias"),
      value = Some("modified_evVarValue"), interpolated = Some("modified_simpleInterpolatedValue"))

    // Verify modification
    simpleAwait(VampPersistence.persistenceDao.update(envVarId, ((e: EnvironmentVariable) => modifiedEnvVar)))
    assert(simpleAwait(VampPersistence.persistenceDao.read(envVarId)) == modifiedEnvVar)

    // Verify that we cannot delete, update or retrieve an object that doesn't exist
    the[InvalidObjectIdException[_]] thrownBy(simpleAwait(VampPersistence.persistenceDao.read(Id[EnvironmentVariable]("non_existent"))))
    the[InvalidObjectIdException[_]] thrownBy(simpleAwait(VampPersistence.persistenceDao.deleteObject(Id[EnvironmentVariable]("non_existent"))))
    the[InvalidObjectIdException[_]] thrownBy(simpleAwait(VampPersistence.persistenceDao.update(Id[EnvironmentVariable]("non_existent"), (e: EnvironmentVariable) => e.copy())))

    // Verify Deletion
    simpleAwait(VampPersistence.persistenceDao.deleteObject(envVarId))
    the[InvalidObjectIdException[_]] thrownBy(simpleAwait(VampPersistence.persistenceDao.read(envVarId)))


    // Verify the retrieval of all objects
    val envVar_template = EnvironmentVariable(name = "envVar_#", alias = Some("envVarAlias_#"),
      value = Some("evVarValue_#"), interpolated = Some("interpolatedValue_#"))
    val envVarList: List[EnvironmentVariable] = (1 to 1000).toList.map(
      i => EnvironmentVariable(name = envVar_template.name.replace("#", s"${i}"),
        alias = envVar_template.alias.map(v => v.replace("#", s"${i}")),
        value = envVar_template.value.map(v => v.replace("#", s"${i}")),
        interpolated = envVar_template.interpolated.map(v => v.replace("#", s"${i}"))
      )
    )

    // Create 100 objects in paralel
    envVarList.grouped(20).toList.map(
      objectList => simpleAwait(Future.sequence(objectList.map( eVar => VampPersistence.persistenceDao.create(eVar))))
    )

    // Reindex objects so they are searchable
    Thread.sleep(2000)


    val response = simpleAwait(VampPersistence.persistenceDao.getAll(environmentVariableSerilizationSpecifier))
    assert(response.size == envVarList.size && envVarList.forall(e => response.exists(_ == e)))
  }

}
