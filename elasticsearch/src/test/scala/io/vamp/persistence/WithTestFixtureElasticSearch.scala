package io.vamp.persistence

import io.vamp.common.config.WithTestFixture
import org.scalatest.{fixture, OneInstancePerTest}

/**
  * Created by mihai on 9/20/17.
  */
class WithTestFixtureElasticSearch extends fixture.FlatSpec with OneInstancePerTest with WithTestFixture {
  override def createFixture(test: OneArgTest): WithTestFixtureElasticSearch.this.type = ???

  override type FixtureParam = this.type
}
