package io.vamp.persistence.refactor

import io.vamp.common.Namespace
import io.vamp.model.artifact._
import io.vamp.model.reader.Percentage
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}

/**
 * Created by mihai on 11/10/17.
 */
class ESPersistenceTest_Gateway extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  behavior of "EsDao"
  it should "Correctly Persist Gateway objects" in { implicit namespace: Namespace ⇒
    val gateway1 = Gateway(name = "gateway_1",
      port = Port(name = "port01", alias = Some("portAlias01"), value = Some("value01"), number = 1, `type` = Port.Type.Http),
      service = Some(GatewayService(host = "localhost01", port = Port(name = "port01_01", alias = Some("portAlias01_01"), value = Some("value01_01"), number = 11, `type` = Port.Type.Tcp))),
      sticky = Some(Gateway.Sticky.Route), virtualHosts = List("h001", "h002", "h003"),
      routes = List[Route](
        DefaultRoute(name = "defaultReout01", path = GatewayPath(source = "source01", segments = List[String]("seg01", "seg02")),
            weight = Some(Percentage(value = 66)), condition = Some(DefaultCondition(name = "cond001", definition = "def001")), conditionStrength = Some(Percentage(value = 55)),
            rewrites = List[Rewrite](
                RewriteReference("rewriteName01"),
                PathRewrite(name = "pathRewrite01", path = "somePath", condition = "someCondition")
            ),
            balance = Some("balance001"),
            targets = List[RouteTarget](ExternalRouteTarget("sumeUrl_007"), InternalRouteTarget(name = "internalRouteTarget", host = Some("host98"), port = 3306))
        ),
        RouteReference(name = "routeReference01", path = GatewayPath(source = "source02", segments = List[String]("seg03", "seg04")))
      ),
      deployed = false)

    // Create and retrieve; See that the object is there
    val gateway1Id = simpleAwait(VampPersistence().create(gateway1))
    assert(simpleAwait(VampPersistence().read(gateway1Id)) == gateway1)
  }

}
