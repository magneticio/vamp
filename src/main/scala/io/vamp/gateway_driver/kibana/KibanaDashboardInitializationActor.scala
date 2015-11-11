package io.vamp.gateway_driver.kibana

import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor.DocumentDefinition

import scala.io.Source

class KibanaDashboardInitializationActor extends ElasticsearchInitializationActor with GatewayDriverNotificationProvider {

  lazy val elasticsearchUrl = KibanaDashboardActor.elasticsearchUrl

  override lazy val documents: List[DocumentDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString
    List(DocumentDefinition(".kibana", "index-pattern", KibanaDashboardActor.logstashIndex, load("kibana_init")))
  }
}
