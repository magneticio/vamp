package io.vamp.persistence.refactor

import io.vamp.persistence.refactor.api.SimpleArtifactPersistenceDao
import io.vamp.persistence.refactor.serialization.VampJsonFormats

/**
  * Created by mihai on 11/10/17.
  */
trait VampPersistenceAssembly extends VampJsonFormats {

  def persistenceDao: SimpleArtifactPersistenceDao
}
