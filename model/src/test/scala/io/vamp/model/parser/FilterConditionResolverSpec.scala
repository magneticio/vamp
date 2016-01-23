package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class FilterConditionResolverSpec extends FlatSpec with Matchers with FilterConditionResolver {

  "FilterConditionResolver" should "resolve" in {
    resolve("User-Agent != Firefox or User-Agent = Firefox") shouldBe True
    resolve("User-Agent != Firefox && User-Agent = Firefox") shouldBe False
  }
}
