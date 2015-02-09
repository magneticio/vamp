package io.magnetic.vamp_common.json

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.io.Source
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class Json2CaseClassTest extends FlatSpec with Matchers {

  "A Json2CaseClass" should "generate case classes from the test resource" in {
    Json2CaseClass.generate("io.magnetic", "Input1", res("input1.json")) should be(res("output1.txt"))
  }

  it should "generate case classes with exclusion from the test resource" in {
    val result = Json2CaseClass.generate("io.magnetic", "Input2", res("input2.json"), Set("var3", "var5/var5Var1"))
    println(result)
    result should be(res("output2.txt"))
  }

  def res(path: String): String = Source.fromURL(getClass.getResource(path)) mkString
}
