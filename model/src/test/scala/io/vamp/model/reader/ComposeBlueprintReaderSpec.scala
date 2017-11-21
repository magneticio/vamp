package io.vamp.model.reader

import io.vamp.common.{RestrictedString, RootAnyMap}
import io.vamp.model.artifact._
import io.vamp.model.notification.UnsupportedProtocolError
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest._

@RunWith(classOf[JUnitRunner])
class ComposeBlueprintReaderSpec
    extends FlatSpec
    with Matchers
    with ReaderSpec {

  "ComposeBlueprintReader" should "parse a blueprint" in {
    val composeReader = ComposeBlueprintReader.fromDockerCompose("TestName")(res("compose/compose1.yml"))

    composeReader.result should equal(
      DefaultBlueprint(
        "TestName",
        RootAnyMap.empty,
        List(
          Cluster(
            "catalogue-db",
            RootAnyMap.empty,
            List(Service(DefaultBreed("catalogue-db:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/catalogue-db"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None), EnvironmentVariable("MYSQL_ROOT_PASSWORD", None, Some("${MYSQL_ROOT_PASSWORD}"), None), EnvironmentVariable("MYSQL_ALLOW_EMPTY_PASSWORD", None, Some("true"), None), EnvironmentVariable("MYSQL_DATABASE", None, Some("socksdb"), None)), List(), List(), Map(), None), List(), None, List(), None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "cart",
            RootAnyMap.empty,
            List(
              Service(DefaultBreed("cart:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/cart"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)
            ),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "rabbitmq",
            RootAnyMap.empty,
            List(Service(DefaultBreed("rabbitmq:1.0.0", RootAnyMap.empty, Deployable("container/docker", "rabbitmq:3"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "user-sim",
            RootAnyMap.empty,
            List(Service(DefaultBreed("user-sim:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/load-test"), List(), List(), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap(Map("docker" → RestrictedString("-d 60 -r 200 -c 2 -h front-end"))), None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "user-db",
            RootAnyMap.empty,
            List(Service(DefaultBreed("user-db:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/user-db"), List(Port("port_27017", None, Some("27017"), 27017, Port.Type.Http)), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "payment",
            RootAnyMap.empty,
            List(Service(DefaultBreed("payment:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/payment"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "cart-db",
            RootAnyMap.empty,
            List(Service(DefaultBreed("cart-db:1.0.0", RootAnyMap.empty, Deployable("container/docker", "mongo"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "catalogue",
            RootAnyMap.empty,
            List(Service(DefaultBreed("catalogue:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/catalogue"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "orders",
            RootAnyMap.empty,
            List(Service(DefaultBreed("orders:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/orders"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "orders-db",
            RootAnyMap.empty,
            List(Service(DefaultBreed("orders-db:1.0.0", RootAnyMap.empty, Deployable("container/docker", "mongo"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "user",
            RootAnyMap.empty,
            List(Service(DefaultBreed("user:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/user"), List(), List(EnvironmentVariable("MONGO_HOST", None, Some("$user-db.host:$user-db.ports.port_27017"), None), EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map("user-db" → BreedReference("user-db:1.0.0")), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "front-end",
            RootAnyMap.empty,
            List(Service(DefaultBreed("front-end:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/front-end"), List(Port("port_8079", None, Some("8079"), 8079, Port.Type.Http)), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "shipping",
            RootAnyMap.empty,
            List(Service(DefaultBreed("shipping:1.0.0", RootAnyMap.empty, Deployable("container/docker", "weaveworksdemos/shipping"), List(), List(EnvironmentVariable("reschedule", None, Some("on-node-failure"), None)), List(), List(), Map(), None), List(), None, List(), None, None, RootAnyMap.empty, None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty)
        ),
        List(),
        List(),
        Map()))
  }

  it should "read the wordpress example yml" in {
    ComposeBlueprintReader.fromDockerCompose("TestName")(res("compose/compose3.yml")).result should equal(
      DefaultBlueprint(
        "TestName",
        RootAnyMap.empty,
        List(
          Cluster(
            "db",
            RootAnyMap.empty,
            List(Service(DefaultBreed("db:1.0.0", RootAnyMap.empty, Deployable("container/docker", "mysql:5.7"), List(Port("port_3306", None, Some("3306"), 3306, Port.Type.Http)), List(EnvironmentVariable("MYSQL_ROOT_PASSWORD", None, Some("wordpress")), EnvironmentVariable("MYSQL_DATABASE", None, Some("wordpress")), EnvironmentVariable("MYSQL_USER", None, Some("wordpress")), EnvironmentVariable("MYSQL_PASSWORD", None, Some("wordpress"))), List(), List(), Map(), None), List(), None, List(), None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty),
          Cluster(
            "wordpress",
            RootAnyMap.empty,
            List(
              Service(DefaultBreed("wordpress:1.0.0", RootAnyMap.empty, Deployable("container/docker", "wordpress:latest"), List(Port("port_80", None, Some("80"), 80, Port.Type.Http)), List(EnvironmentVariable("WORDPRESS_DB_HOST", None, Some("$db.host:$db.ports.port_3306")), EnvironmentVariable("WORDPRESS_DB_PASSWORD", None, Some("wordpress"))), List(), List(), Map("db" → BreedReference("db:1.0.0")), None), List(), None, List(), None)),
            List(),
            None,
            None,
            None,
            RootAnyMap.empty)),
        List(),
        List(),
        Map()))
  }

  "ComposePortReader" should "parse ports" in {
    ComposePortReader.read(res("compose/compose2.yml")).result should equal {
      List(
        Port("port_3000", None, Some("3000"), 3000, Port.Type.Http),
        Port("port_3000", None, Some("3000"), 3000, Port.Type.Http),
        Port("port_8000", None, Some("8000"), 8000, Port.Type.Http),
        Port("port_8080", None, Some("8080"), 8080, Port.Type.Http),
        Port("port_22", None, Some("22"), 22, Port.Type.Http),
        Port("port_8001", None, Some("8001"), 8001, Port.Type.Http),
        Port("port_5000", None, Some("5000"), 5000, Port.Type.Http),
        Port("port_6060", None, Some("6060"), 6060, Port.Type.Tcp))
    }
  }

  it should "throw an UnsupportedProtocolError on udp or any non supported port type" in {
    expectedError[UnsupportedProtocolError] {
      ComposePortReader.read(res("compose/compose4.yml"))
    }
  }

}
