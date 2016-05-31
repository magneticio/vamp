package io.vamp.model.resolver

import io.vamp.model.artifact.{ HostReference, LocalReference, TraitReference }
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class TraitResolverSpec extends FlatSpec with Matchers with TraitResolver {

  "TraitResolver" should "split into parts value with no references" in {
    nodes("abc") shouldBe
      List(StringNode("abc"))
  }

  it should "split into parts value with simple reference" in {
    nodes("$port") shouldBe
      List(VariableNode(LocalReference("port")))
  }

  it should "split into parts value with simple reference and prefix" in {
    nodes("a$port") shouldBe
      List(StringNode("a"), VariableNode(LocalReference("port")))
  }

  it should "split into parts value with simple reference and postfix" in {
    nodes("$port/") shouldBe
      List(VariableNode(LocalReference("port")), StringNode("/"))
  }

  it should "split into parts value with simple reference, prefix and postfix" in {
    nodes(s"http://$${host}/") shouldBe
      List(StringNode("http://"), VariableNode(LocalReference("host")), StringNode("/"))
  }

  it should "split into parts value with trait reference" in {
    nodes(s"$${port}") shouldBe
      List(VariableNode(LocalReference("port")))
  }

  it should "split into parts value with trait reference and prefix" in {
    nodes(s"a$${db.host}") shouldBe
      List(StringNode("a"), VariableNode(HostReference("db")))
  }

  it should "split into parts value with trait reference and postfix" in {
    nodes(s"$${port}/") shouldBe
      List(VariableNode(LocalReference("port")), StringNode("/"))
  }

  it should "split into parts value with trait reference, prefix and postfix" in {
    nodes(s"{$${es.constants.port}}") shouldBe
      List(StringNode("{"), VariableNode(TraitReference("es", "constants", "port")), StringNode("}"))
  }

  it should "split into parts sequence" in {
    nodes("/$a$b$c/") shouldBe
      List(StringNode("/"), VariableNode(LocalReference("a")), VariableNode(LocalReference("b")), VariableNode(LocalReference("c")), StringNode("/"))
  }

  it should "ignore $$" in {
    nodes("a$$b") shouldBe
      List(StringNode("a$b"))
  }

  it should "ignore $$$$" in {
    nodes("a$$$$b") shouldBe
      List(StringNode("a$$b"))
  }

  it should "$$ with immediate reference" in {
    nodes("$$$d") shouldBe
      List(StringNode("$"), VariableNode(LocalReference("d")))
  }

  it should "ignore $$ with reference" in {
    nodes("a$$b$d") shouldBe
      List(StringNode("a$b"), VariableNode(LocalReference("d")))
  }

  it should "split multiple references" in {
    nodes(s"http://$${api.host}:$$port/api/$${api.constants.version}/$${resource}/$$id") shouldBe
      List(
        StringNode("http://"),
        VariableNode(HostReference("api")),
        StringNode(":"),
        VariableNode(LocalReference("port")),
        StringNode("/api/"),
        VariableNode(TraitReference("api", "constants", "version")),
        StringNode("/"),
        VariableNode(LocalReference("resource")),
        StringNode("/"),
        VariableNode(LocalReference("id"))
      )
  }

  it should "split into parts host reference without {}" in {
    nodes("a$db.host:") shouldBe
      List(StringNode("a"), VariableNode(HostReference("db")), StringNode(":"))
  }

  it should "split into parts trait reference without {}" in {
    nodes("a$api.constants.version/") shouldBe
      List(StringNode("a"), VariableNode(TraitReference("api", "constants", "version")), StringNode("/"))
  }
}
