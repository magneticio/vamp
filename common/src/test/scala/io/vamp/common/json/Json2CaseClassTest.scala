package io.vamp.common.json

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.io.Source
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class Json2CaseClassTest extends FlatSpec with Matchers {

  "A Json2CaseClass" should "generate case classes from the test resource" in {
    Json2CaseClass.buildCaseClass("io", "Input1", res("input1.json")) shouldBe res("output1.txt")
  }

  it should "generate case classes with exclusion from the test resource" in {
    Json2CaseClass.buildCaseClass("io", "Input2", res("input2.json"), Set("var3", "var5/var5Var1")) shouldBe res("output2.txt")
  }

  it should "generate case classes with proper list of objects" in {
    Json2CaseClass.buildCaseClass("io", "Input3", res("input3.json")) should be(res("output3.txt"))
  }

  it should "generate case classes with optional fields" in {
    Json2CaseClass.buildCaseClass("io", "Input4", res("input4.json"), Set(), Set("var1")) shouldBe res("output4.txt")
  }

  def res(path: String): String = Source.fromURL(getClass.getResource(path)) mkString
}
