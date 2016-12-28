package io.vamp.model.reader

import io.vamp.model.reader.YamlSourceReader._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class YamlSourceReaderTest extends FlatSpec with Matchers {

  "YamlSourceReaderTest" should "consume for find" in {

    val yaml = YamlSourceReader(Map(
      "a" → "b",
      "c" → Map(
        "d" → "e",
        "f" → "g"
      ),
      "h" → "i",
      "j" → Map(
        "k" → "l",
        "m" → "n"
      ),
      "o" → List("p", "q"),
      "s" → List("t", "v")
    ))

    yaml.find[String]("a")
    yaml.find[String]("c" :: "d" :: Nil)
    yaml.find[List[String]]("o")

    yaml.consumed should be(
      Map(
        "a" → "b",
        "c" → Map(
          "d" → "e"
        ),
        "o" → List("p", "q")
      )
    )

    yaml.notConsumed should be(
      Map(
        "c" → Map(
          "f" → "g"
        ),
        "h" → "i",
        "j" → Map(
          "k" → "l",
          "m" → "n"
        ),
        "s" → List("t", "v")
      )
    )
  }

  it should "consume for find and set" in {

    val yaml = YamlSourceReader(Map(
      "a" → "b",
      "j" → Map(
        "m" → "n"
      )
    ))

    yaml.set("c" :: "d" :: Nil, Some("e"))
    yaml.set("c" :: "f" :: Nil, Some("g"))
    yaml.set("h" :: Nil, Some("i"))
    yaml.set("j" :: "k" :: Nil, Some("l"))

    yaml.find[String]("a")
    yaml.find[String]("c" :: "d" :: Nil)

    yaml.consumed should be(
      Map(
        "a" → "b",
        "c" → Map(
          "d" → "e"
        )
      )
    )

    yaml.notConsumed should be(
      Map(
        "c" → Map(
          "f" → "g"
        ),
        "h" → "i",
        "j" → Map(
          "k" → "l",
          "m" → "n"
        )
      )
    )
  }

  it should "consume for pull" in {

    val yaml = YamlSourceReader(Map(
      "a" → "b",
      "c" → Map(
        "d" → "e",
        "f" → "g"
      ),
      "h" → "i",
      "j" → Map(
        "k" → "l",
        "m" → "n",
        "o" → Map(
          "p" → "q"
        )
      )
    ))

    yaml.find[String]("a")
    yaml.find[String]("c" :: "d" :: Nil)
    yaml.find[YamlSourceReader]("j").get.pull()

    yaml.consumed should be(
      Map(
        "a" → "b",
        "c" → Map(
          "d" → "e"
        ),
        "j" → Map(
          "k" → "l",
          "m" → "n"
        )
      )
    )

    yaml.notConsumed should be(
      Map(
        "c" → Map(
          "f" → "g"
        ),
        "h" → "i",
        "j" → Map(
          "o" → Map(
            "p" → "q"
          )
        )
      )
    )
  }

  it should "consume for flatten" in {

    val yaml = YamlSourceReader(Map(
      "a" → "b",
      "c" → Map(
        "d" → "e",
        "f" → "g"
      ),
      "h" → "i",
      "j" → Map(
        "k" → "l",
        "m" → "n"
      )
    ))

    yaml.find[String]("a")
    yaml.find[String]("c" :: "d" :: Nil)
    yaml.find[YamlSourceReader]("j").get.flatten()

    yaml.consumed should be(
      Map(
        "a" → "b",
        "c" → Map(
          "d" → "e"
        ),
        "j" → Map(
          "k" → "l",
          "m" → "n"
        )
      )
    )

    yaml.notConsumed should be(
      Map(
        "c" → Map(
          "f" → "g"
        ),
        "h" → "i"
      )
    )
  }

  it should "consume for flatten all" in {

    val yaml = YamlSourceReader(Map(
      "a" → "b",
      "c" → Map(
        "d" → "e",
        "f" → "g"
      ),
      "h" → "i",
      "j" → Map(
        "k" → "l",
        "m" → "n"
      )
    ))

    yaml.flatten()

    yaml.consumed should be(
      Map(
        "a" → "b",
        "c" → Map(
          "d" → "e",
          "f" → "g"
        ),
        "h" → "i",
        "j" → Map(
          "k" → "l",
          "m" → "n"
        )
      )
    )

    yaml.notConsumed should be(Map())
  }

  it should "consume for flatten all, exclude keys" in {

    val yaml = YamlSourceReader(Map(
      "a" → "b",
      "c" → Map(
        "d" → "e",
        "f" → "g"
      ),
      "h" → "i",
      "j" → Map(
        "k" → "l",
        "m" → "n"
      )
    ))

    yaml.flatten((key: String) ⇒ key != "a")

    yaml.consumed should be(
      Map(
        "c" → Map(
          "d" → "e",
          "f" → "g"
        ),
        "h" → "i",
        "j" → Map(
          "k" → "l",
          "m" → "n"
        )
      )
    )

    yaml.notConsumed should be(Map("a" → "b"))
  }

  it should "consume list but not elements" in {

    val yaml = YamlSourceReader(Map(
      "services" → List(
        Map(
          "breed" → Map(
            "ref" → "sava:1.0.0"
          ),
          "scale" → Map(
            "cpu" → "0.2"
          )
        )
      )
    ))

    yaml.find[List[YamlSourceReader]]("services")

    yaml.consumed should be(Map())

    yaml.notConsumed should be(Map(
      "services" → List(
        Map(
          "breed" → Map(
            "ref" → "sava:1.0.0"
          ),
          "scale" → Map(
            "cpu" → "0.2"
          )
        )
      )
    ))
  }

  it should "consume list with simple elements" in {

    val yaml = YamlSourceReader(Map("services" → List("a", "b")))

    yaml.find[List[_]]("services")

    yaml.consumed should be(Map("services" → List("a", "b")))

    yaml.notConsumed should be(Map())
  }

  it should "consume list element" in {

    val yaml = YamlSourceReader(Map(
      "services" → List(
        Map(
          "breed" → Map(
            "ref" → "sava:1.0.0"
          ),
          "scale" → Map(
            "cpu" → "0.2"
          )
        )
      )
    ))

    val service = yaml.find[List[YamlSourceReader]]("services").get.head

    service.find[String]("breed" :: "ref" :: Nil)

    yaml.consumed should be(
      Map("services" → List(
        Map(
          "breed" → Map(
            "ref" → "sava:1.0.0"
          )
        )
      ))
    )

    yaml.notConsumed should be(Map(
      "services" → List(
        Map(
          "scale" → Map(
            "cpu" → "0.2"
          )
        )
      )
    ))
  }
}
