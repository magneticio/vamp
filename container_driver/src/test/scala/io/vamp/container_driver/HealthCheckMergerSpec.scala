package io.vamp.container_driver

import io.vamp.model.artifact.{ HealthCheck, Port }
import io.vamp.model.reader.{ Time }
import org.junit.runner.RunWith
import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HealthCheckMergerSpec extends FlatSpec with Matchers with HealthCheckMerger {

  val testHealthCheck = Some(List(HealthCheck("/", "webport", Time(5), Time(5), Time(5), 5, "HTTP")))
  val testHealthCheckTwo = Some(List(HealthCheck("/two", "someport", Time(5), Time(5), Time(5), 5, "HTTP")))

  val testPort = List(Port("webport", None, None, 8080, Port.Type.Http))
  val testPortTwo = List(Port("someport", None, None, 9000, Port.Type.Http))

  "HealthCheckMerger" should "return no health checks if service level is an empty list" in {
    val mergeResult = mergeHealthChecks(testHealthCheck, Some(List()), testHealthCheck, testPort)

    mergeResult should equal(List())
  }

  it should "return only the service level health check if all paths are equal" in {
    val mergeResult = mergeHealthChecks(
      testHealthCheck,
      testHealthCheck.map(_.map(_.copy(failures = 10))),
      testHealthCheck,
      testPort)

    mergeResult should equal(List(HealthCheck("/", "webport", Time(5), Time(5), Time(5), 10, "HTTP")))
  }

  it should "merge cluster and breed health if service is not defined" in {
    val mergeResult = mergeHealthChecks(
      testHealthCheck,
      None,
      testHealthCheckTwo, testPort ++ testPortTwo)

    mergeResult should equal(testHealthCheckTwo.get ++ testHealthCheck.get)
  }

  it should "return an empty list if all are not defined" in {
    val mergeResult = mergeHealthChecks(None, None, None, List())

    mergeResult should equal(List())
  }

  it should "give cluster precedence over breed on same path and port" in {
    val testHealthCheckFailDiff = testHealthCheck.map(_.map(_.copy(failures = 20)))
    val mergeResult = mergeHealthChecks(
      testHealthCheck,
      None,
      testHealthCheckFailDiff,
      testPort)

    mergeResult should equal(testHealthCheckFailDiff.get)
  }

  it should "merge service and cluster if they have different paths but ports are available for that particular service" in {
    val mergeResult = mergeHealthChecks(
      None,
      Some(List(HealthCheck("/", "webport", Time(5), Time(5), Time(5), 5, "HTTP"))),
      Some(List(HealthCheck("/2", "webport", Time(5), Time(5), Time(5), 5, "HTTP"))),
      testPort)

    mergeResult should equal(List(
      HealthCheck("/", "webport", Time(5), Time(5), Time(5), 5, "HTTP"),
      HealthCheck("/2", "webport", Time(5), Time(5), Time(5), 5, "HTTP")))
  }

  it should "merge cluster if cluster overrides service and breed" in {
    val mergeResult = mergeHealthChecks(
      testHealthCheck,
      testHealthCheck,
      testHealthCheck,
      testPort)

    mergeResult should equal(testHealthCheck.get)
  }

  it should "merge not the same path if ports are different" in {
    val testHealthCheckDiffPath = testHealthCheck.map(_.map(_.copy(port = "someport")))

    val mergeResult = mergeHealthChecks(None, testHealthCheckDiffPath, testHealthCheck, testPort ++ testPortTwo)

    mergeResult should equal(testHealthCheckDiffPath.get)
  }

  it should "merge same ports if path is different" in {
    val testHealthCheckDiffPath = testHealthCheck.map(_.map(_.copy(path = "/diff")))

    val mergeResult = mergeHealthChecks(None, testHealthCheckDiffPath, testHealthCheck, testPort)

    mergeResult should equal(testHealthCheckDiffPath.get ++ testHealthCheck.get)
  }

  it should "merge a larger list" in {
    val healthChecks = (1 to 30).map(i ⇒ HealthCheck(s"$i", "webport", Time(5), Time(5), Time(5), 5, "HTTP")).toList
    val mergeResult = mergeHealthChecks(
      Some(healthChecks.take(10)),
      Some(healthChecks.slice(10, 20)),
      Some(healthChecks.slice(20, 30)),
      testPort)

    mergeResult should equal(healthChecks.drop(10))
  }

  it should "return the breed level health checks is others are not defined" in {
    val mergeResult = mergeHealthChecks(testHealthCheck, None, None, testPort)

    mergeResult should equal(testHealthCheck.get)
  }

}
