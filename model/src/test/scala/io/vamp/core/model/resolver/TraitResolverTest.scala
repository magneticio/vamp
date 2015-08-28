package io.vamp.core.model.resolver

import io.vamp.core.model.artifact.{HostReference, LocalReference, TraitReference}
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class TraitResolverTest extends FlatSpec with Matchers with TraitResolver {

  "TraitResolver" should "split into parts value with no references" in {
    partsFor("abc") should have(
      '_1(List("abc")),
      '_2(Nil)
    )
  }

  it should "split into parts value with simple reference" in {
    partsFor("$port") should have(
      '_1(List("", "")),
      '_2(List(LocalReference("port")))
    )
  }

  it should "split into parts value with simple reference and prefix" in {
    partsFor("a$port") should have(
      '_1(List("a", "")),
      '_2(List(LocalReference("port")))
    )
  }

  it should "split into parts value with simple reference and postfix" in {
    partsFor("$port/") should have(
      '_1(List("", "/")),
      '_2(List(LocalReference("port")))
    )
  }

  it should "split into parts value with simple reference, prefix and postfix" in {
    partsFor(s"http://$${host}/") should have(
      '_1(List("http://", "/")),
      '_2(List(LocalReference("host")))
    )
  }

  it should "split into parts value with trait reference" in {
    partsFor(s"$${port}") should have(
      '_1(List("", "")),
      '_2(List(LocalReference("port")))
    )
  }

  it should "split into parts value with trait reference and prefix" in {
    partsFor(s"a$${db.host}") should have(
      '_1(List("a", "")),
      '_2(List(HostReference("db")))
    )
  }

  it should "split into parts value with trait reference and postfix" in {
    partsFor(s"$${port}/") should have(
      '_1(List("", "/")),
      '_2(List(LocalReference("port")))
    )
  }

  it should "split into parts value with trait reference, prefix and postfix" in {
    partsFor(s"{$${es.constants.port}}") should have(
      '_1(List("{", "}")),
      '_2(List(TraitReference("es", "constants", "port")))
    )
  }

  it should "split into parts sequence" in {
    partsFor("/$a$b$c/") should have(
      '_1(List("/", "", "", "/")),
      '_2(List(LocalReference("a"), LocalReference("b"), LocalReference("c")))
    )
  }

  it should "ignore $$" in {
    partsFor("a$$b") should have(
      '_1(List("a", "b")),
      '_2(List(LocalReference("$")))
    )
  }

  it should "ignore $$$$" in {
    partsFor("a$$$$b") should have(
      '_1(List("a", "", "b")),
      '_2(List(LocalReference("$"), LocalReference("$")))
    )
  }

  it should "$$ with immediate reference" in {

    partsFor("$$$d") should have(
      '_1(List("", "", "")),
      '_2(List(LocalReference("$"), LocalReference("d")))
    )
  }

  it should "ignore $$ with reference" in {
    partsFor("a$$b$d") should have(
      '_1(List("a", "b", "")),
      '_2(List(LocalReference("$"), LocalReference("d")))
    )
  }

  it should "split multiple references" in {
    partsFor(s"http://$${api.host}:$$port/api/$${api.constants.version}/$${resource}/$$id") should have(
      '_1(List("http://", ":", "/api/", "/", "/", "")),
      '_2(List(HostReference("api"), LocalReference("port"), TraitReference("api", "constants", "version"), LocalReference("resource"), LocalReference("id")))
    )
  }

  it should "split into parts host reference without {}" in {
    partsFor("a$db.host:") should have(
      '_1(List("a", ":")),
      '_2(List(HostReference("db")))
    )
  }

  it should "split into parts trait reference without {}" in {
    partsFor("a$api.constants.version/") should have(
      '_1(List("a", "/")),
      '_2(List(TraitReference("api", "constants", "version")))
    )
  }
}
