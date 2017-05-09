package io.vamp.common.config

import io.vamp.common.util.ObjectUtil
import io.vamp.common.{ Config, ConfigFilter, Namespace, NamespaceProvider }
import org.json4s.{ DefaultFormats, Formats }
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ BeforeAndAfterEach, FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class ConfigSpec extends FlatSpec with Matchers with BeforeAndAfterEach with NamespaceProvider {

  implicit val namespace: Namespace = Namespace("default")

  private implicit val formats: Formats = DefaultFormats

  override def afterEach() = Config.load(Map())

  "Config" should "retrieve data from configuration" in {
    Config.load(Map("a.b.c" → 456, "q" → "qwerty", "f" → true))
    Config.int("a.b.c")() shouldBe 456
    Config.string("q")() shouldBe "qwerty"
    Config.boolean("f")() shouldBe true
  }

  it should "marshall input" in {
    Config.marshall(
      Map(
        "a" → 456,
        "q" → "qwerty",
        "f" → true
      )
    ) shouldBe
      """{
        |  "a":456,
        |  "q":"qwerty",
        |  "f":true
        |}""".stripMargin

    Config.marshall(
      Map(
        "a" → Map(
          "q" → "qwerty",
          "f" → true
        )
      )
    ) shouldBe
      """{
        |  "a":{
        |    "q":"qwerty",
        |    "f":true
        |  }
        |}""".stripMargin

    Config.marshall(
      Map(
        "a" → List(
          Map(
            "q" → "qwerty",
            "f" → 5
          )
        )
      )
    ) shouldBe
      """{
        |  "a":[
        |    {
        |      "q":"qwerty",
        |      "f":5
        |    }
        |  ]
        |}""".stripMargin

    Config.marshall(
      Map(
        "a.b" → 456,
        "q.t" → "qwerty",
        "f" → true
      )
    ) shouldBe
      """{
        |  "a.b":456,
        |  "q.t":"qwerty",
        |  "f":true
        |}""".stripMargin

    Config.marshall(
      Map(
        "a.b" → Map(
          "q.z" → "qwerty",
          "f" → true
        )
      )
    ) shouldBe
      """{
        |  "a.b":{
        |    "q.z":"qwerty",
        |    "f":true
        |  }
        |}""".stripMargin

    Config.marshall(
      Map(
        "a.b" → List(
          Map(
            "q.x" → "qwerty",
            "f" → 5
          )
        )
      )
    ) shouldBe
      """{
        |  "a.b":[
        |    {
        |      "q.x":"qwerty",
        |      "f":5
        |    }
        |  ]
        |}""".stripMargin
  }

  it should "unmarshall input" in {
    Config.unmarshall(
      """{
        |  "a":456,
        |  "q":"qwerty",
        |  "f":true
        |}""".stripMargin
    ) shouldBe
      Map(
        "a" → 456,
        "q" → "qwerty",
        "f" → true
      )

    Config.unmarshall(
      """{
        |  "a":{
        |    "q":"qwerty",
        |    "f":true
        |  }
        |}""".stripMargin
    ) shouldBe
      Map(
        "a" → Map(
          "q" → "qwerty",
          "f" → true
        )
      )

    Config.unmarshall(
      """{
        |  "a":[
        |    {
        |      "q":"qwerty",
        |      "f":5
        |    }
        |  ]
        |}""".stripMargin
    ) shouldBe
      Map(
        "a" → List(
          Map(
            "q" → "qwerty",
            "f" → 5
          )
        )
      )

    Config.unmarshall(
      """{
        |  "a.b":456,
        |  "q.t":"qwerty",
        |  "f":true
        |}""".stripMargin
    ) shouldBe
      Map(
        "q" →
          Map(
            "t" → "qwerty"
          ),
        "a" → Map(
          "b" → 456
        ),
        "f" → true
      )

    Config.unmarshall(
      """{
        |  "a.b":{
        |    "q.z":"qwerty",
        |    "f":true
        |  }
        |}""".stripMargin
    ) shouldBe
      Map(
        "a" →
          Map(
            "b" → Map(
              "q" → Map(
                "z" → "qwerty"
              ),
              "f" → true
            )
          )
      )

    Config.unmarshall(
      """{
        |  "a.b":[
        |    {
        |      "q.x":"qwerty",
        |      "f":5
        |    }
        |  ]
        |}""".stripMargin
    ) shouldBe
      Map(
        "a" → Map(
          "b" → List(
            Map(
              "q" → Map(
                "x" → "qwerty"
              ),
              "f" → 5
            )
          )
        )
      )
  }

  it should "marshall unmarshall input" in {
    val input1 = Map("a" → 456, "q" → "qwerty", "f" → true)
    Config.unmarshall(Config.marshall(input1)) shouldBe input1

    val input2 = Map("a" → Map("q" → "qwerty", "f" → true))
    Config.unmarshall(Config.marshall(input2)) shouldBe input2

    val input3 = Map("a" → List(Map("q" → "qwerty", "f" → 5)))
    Config.unmarshall(Config.marshall(input3)) shouldBe input3

    val input4 = Map("q" → Map("t" → "qwerty"), "a" → Map("b" → 456), "f" → true)
    Config.unmarshall(Config.marshall(input4)) shouldBe input4

    val input5 = Map("a" → Map("b" → Map("q" → Map("z" → "qwerty"), "f" → true)))
    Config.unmarshall(Config.marshall(input5)) shouldBe input5

    val input6 = Map("a" → Map("b" → List(Map("q" → Map("x" → "qwerty"), "f" → 5))))
    Config.unmarshall(Config.marshall(input6)) shouldBe input6
  }

  it should "load input" in {
    val input1 = Map("a" → 456, "q" → "qwerty", "f" → true)
    Config.load(Config.unmarshall(Config.marshall(input1)))
    Config.export(Config.Type.dynamic, flatten = false) shouldBe input1

    val input2 = Map("a" → Map("q" → "qwerty", "f" → true))
    Config.load(Config.unmarshall(Config.marshall(input2)))
    Config.export(Config.Type.dynamic, flatten = false) shouldBe input2

    val input3 = Map("a" → List(Map("q" → "qwerty", "f" → 5)))
    Config.load(Config.unmarshall(Config.marshall(input3)))
    Config.export(Config.Type.dynamic, flatten = false) shouldBe input3

    val input4 = Map("q" → Map("t" → "qwerty"), "a" → Map("b" → 456), "f" → true)
    Config.load(Config.unmarshall(Config.marshall(input4)))
    Config.export(Config.Type.dynamic, flatten = false) shouldBe input4

    val input5 = Map("a" → Map("b" → Map("q" → Map("z" → "qwerty"), "f" → true)))
    Config.load(Config.unmarshall(Config.marshall(input5)))
    Config.export(Config.Type.dynamic, flatten = false) shouldBe input5

    val input6 = Map("a" → Map("b" → List(Map("q" → Map("x" → "qwerty"), "f" → 5))))
    Config.load(Config.unmarshall(Config.marshall(input6)))
    Config.export(Config.Type.dynamic, flatten = false) shouldBe input6
  }

  it should "load input - flatten" in {
    val input1 = Map("a" → 456, "q" → "qwerty", "f" → true)
    val expected1 = Map("a" → 456, "q" → "qwerty", "f" → true)
    Config.load(Config.unmarshall(Config.marshall(input1)))
    Config.export(Config.Type.dynamic) shouldBe expected1

    val input2 = Map("a" → Map("q" → "qwerty", "f" → true))
    val expected2 = Map("a.q" → "qwerty", "a.f" → true)
    Config.load(Config.unmarshall(Config.marshall(input2)))
    Config.export(Config.Type.dynamic) shouldBe expected2

    val input3 = Map("a" → List(Map("q" → "qwerty", "f" → 5)))
    val expected3 = Map("a" → List(Map("q" → "qwerty", "f" → 5)))
    Config.load(Config.unmarshall(Config.marshall(input3)))
    Config.export(Config.Type.dynamic) shouldBe expected3

    val input4 = Map("q" → Map("t" → "qwerty"), "a" → Map("b" → 456), "f" → true)
    val expected4 = Map("q.t" → "qwerty", "a.b" → 456, "f" → true)
    Config.load(Config.unmarshall(Config.marshall(input4)))
    Config.export(Config.Type.dynamic) shouldBe expected4

    val input5 = Map("a" → Map("b" → Map("q" → Map("z" → "qwerty"), "f" → true)))
    val expected5 = Map("a.b.f" → true, "a.b.q.z" → "qwerty")
    Config.load(Config.unmarshall(Config.marshall(input5)))
    Config.export(Config.Type.dynamic) shouldBe expected5

    val input6 = Map("a" → Map("b" → List(Map("q" → Map("x" → "qwerty"), "f" → 5))))
    val expected6 = Map("a.b" → List(Map("q" → Map("x" → "qwerty"), "f" → 5)))
    Config.load(Config.unmarshall(Config.marshall(input6)))
    Config.export(Config.Type.dynamic) shouldBe expected6
  }

  it should "export applied" in {
    val input1 = Map("a" → 456, "q" → "qwerty", "f" → true)
    Config.load(Map())
    val applied1 = Config.export(Config.Type.applied, flatten = false)
    Config.load(input1)
    Config.export(Config.Type.applied, flatten = false) shouldBe ObjectUtil.merge(input1, applied1)

    val input2 = Map("a" → Map("q" → "qwerty", "f" → true))
    Config.load(Map())
    val applied2 = Config.export(Config.Type.applied, flatten = false)
    Config.load(input2)
    Config.export(Config.Type.applied, flatten = false) shouldBe ObjectUtil.merge(input2, applied2)

    val input3 = Map("a" → List(Map("q" → "qwerty", "f" → 5)))
    Config.load(Map())
    val applied3 = Config.export(Config.Type.applied, flatten = false)
    Config.load(input3)
    Config.export(Config.Type.applied, flatten = false) shouldBe ObjectUtil.merge(input3, applied3)

    val input4 = Map("q" → Map("t" → "qwerty"), "a" → Map("b" → 456), "f" → true)
    Config.load(Map())
    val applied4 = Config.export(Config.Type.applied, flatten = false)
    Config.load(input4)
    Config.export(Config.Type.applied, flatten = false) shouldBe ObjectUtil.merge(input4, applied4)

    val input5 = Map("a" → Map("b" → Map("q" → Map("z" → "qwerty"), "f" → true)))
    Config.load(Map())
    val applied5 = Config.export(Config.Type.applied, flatten = false)
    Config.load(input5)
    Config.export(Config.Type.applied, flatten = false) shouldBe ObjectUtil.merge(input5, applied5)

    val input6 = Map("a" → Map("b" → List(Map("q" → Map("x" → "qwerty"), "f" → 5))))
    Config.load(Map())
    val applied6 = Config.export(Config.Type.applied, flatten = false)
    Config.load(input6)
    Config.export(Config.Type.applied, flatten = false) shouldBe ObjectUtil.merge(input6, applied6)
  }

  it should "override" in {
    Config.load(Map())
    val input = Config.export(Config.Type.system).collect {
      case (k, v: String) ⇒ k → s">>> $v <<<"
    }
    Config.load(Config.unmarshall(Config.marshall(input)))
    val applied = Config.export(Config.Type.applied)

    input.foreach { case (k, v) ⇒ (k → applied(k)) shouldBe (k → v) }
  }

  it should "unmarshall marshall string input" in {
    def validate(input: String) = {
      val source = Config.unmarshall(input)
      val export = Config.marshall(source)

      Config.load(source)
      Config.marshall(Config.export(Config.Type.dynamic, flatten = false)) shouldBe export
    }

    validate("vamp.info.message: Hi!!!")
    validate(
      """vamp:
        |  info:
        |    message: Hi!!!
      """.stripMargin
    )
  }

  it should "unmarshall filter marshall string input" in {
    Config.unmarshall("""
      |vamp.namespace: vamp
      |vamp.info.message: Hi!!!
    """.stripMargin, ConfigFilter({ case (k, _) ⇒ k != "vamp.namespace" })) shouldBe Config.unmarshall("vamp.info.message: Hi!!!")
  }
}
