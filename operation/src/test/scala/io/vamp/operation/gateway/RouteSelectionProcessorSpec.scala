package io.vamp.operation.gateway

import io.vamp.container_driver.{ RoutingGroup, RoutingInstance, RoutingInstancePort }
import io.vamp.model.artifact.RouteSelector
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RouteSelectionProcessorSpec extends FlatSpec with Matchers {

  "RouteSelectionProcessor" should "select by name" in {
    execute("name(sava:1.0)") should be(
      Set("172.17.0.1:8180", "172.17.0.1:8181", "172.17.0.2:8280", "172.17.0.2:8281")
    )
  }

  it should "select by kind" in {
    execute("kind(pod)") should be(
      Set("172.17.0.3:8380", "172.17.0.3:8381", "172.17.0.4:8480", "172.17.0.4:8481")
    )
  }

  it should "select by namespace" in {
    execute("namespace(default)") should be(
      Set("172.17.0.1:8180", "172.17.0.1:8181", "172.17.0.2:8280", "172.17.0.2:8281")
    )
  }

  it should "select by image" in {
    execute("image(magneticio/sava:1.0.*)") should be(
      Set("172.17.0.1:8180", "172.17.0.1:8181", "172.17.0.2:8280", "172.17.0.2:8281")
    )
  }

  it should "select by label" in {
    execute("label(io.vamp.*)(sava:1.1.*)") should be(
      Set("172.17.0.3:8380", "172.17.0.3:8381", "172.17.0.4:8480", "172.17.0.4:8481")
    )
  }

  it should "select by ip" in {
    execute("ip(172.17.0.1)") should be(
      Set("172.17.0.1:8180", "172.17.0.1:8181")
    )
  }

  it should "select by port" in {
    execute("port(8080)") should be(
      Set("172.17.0.1:8180", "172.17.0.2:8280", "172.17.0.3:8380", "172.17.0.4:8480")
    )
  }

  it should "select by 'or' operand" in {
    execute("port(8080) or ip(172.17.0.2)") should be(
      Set("172.17.0.4:8480", "172.17.0.3:8380", "172.17.0.2:8280", "172.17.0.1:8180", "172.17.0.2:8281")
    )
  }

  it should "select by 'and' operand" in {
    execute("port(8080) and ip(172.17.0.2)") should be(
      Set("172.17.0.2:8280")
    )
  }

  it should "select by 'not' operand" in {
    execute("!(port(8080) || ip(172.17.0.2)) && true") should be(
      Set("172.17.0.1:8181", "172.17.0.3:8381", "172.17.0.4:8481")
    )
  }

  it should "select all" in {
    execute("true") should be(
      Set("172.17.0.4:8480", "172.17.0.3:8380", "172.17.0.3:8381", "172.17.0.4:8481", "172.17.0.2:8280", "172.17.0.1:8180", "172.17.0.2:8281", "172.17.0.1:8181")
    )
  }

  it should "select none" in {
    execute("false") should be(
      Set()
    )
  }

  it should "select by port index" in {
    execute("port_index(0)") should be(
      Set("172.17.0.4:8480", "172.17.0.3:8380", "172.17.0.2:8280", "172.17.0.1:8180")
    )
  }

  private def execute(selector: String): Set[String] = {
    val routingGroups = RoutingGroup(
      name = "sava:1.0",
      kind = "app",
      namespace = "default",
      labels = Map(
        "io.vamp.deployment" → "sava:1.0",
        "io.vamp.cluster" → "sava",
        "io.vamp.service" → "sava:1.0.0"
      ),
      image = Option("magneticio/sava:1.0.0"),
      instances = RoutingInstance("172.17.0.1", List(RoutingInstancePort(8080, 8180), RoutingInstancePort(8081, 8181))) :: RoutingInstance("172.17.0.2", List(RoutingInstancePort(8080, 8280), RoutingInstancePort(8081, 8281))) :: Nil
    ) :: RoutingGroup(
        name = "sava:1.1",
        kind = "pod",
        namespace = "vamp",
        labels = Map(
          "io.vamp.deployment" → "sava:1.1",
          "io.vamp.cluster" → "sava",
          "io.vamp.service" → "sava:1.1.0",
          "sava" → "1.1"
        ),
        image = Option("magneticio/sava:1.1.0"),
        instances = RoutingInstance("172.17.0.3", List(RoutingInstancePort(8080, 8380), RoutingInstancePort(8081, 8381))) :: RoutingInstance("172.17.0.4", List(RoutingInstancePort(8080, 8480), RoutingInstancePort(8081, 8481))) :: Nil
      ) :: Nil

    RouteSelectionProcessor.execute(RouteSelector(selector), routingGroups).map(_.url).toSet
  }
}
