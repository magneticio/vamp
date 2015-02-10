package io.magnetic.marathon.client.api

case class HttpConfig(assetsPath: AnyRef, httpPort: Int, httpsPort: Int)

case class EventSubscriber(`type`: String, httpEndpoints: List[AnyRef])

case class MarathonConfig(checkpoint: Boolean, executor: String, failoverTimeout: Int, ha: Boolean, hostname: String, localPortMax: Int, localPortMin: Int, master: String, mesosRole: AnyRef, mesosUser: String, reconciliationInitialDelay: Int, reconciliationInterval: Int, taskLaunchTimeout: Int)

case class ZkFutureTimeout(duration: Int)

case class ZookeeperConfig(zk: String, zkFutureTimeout: ZkFutureTimeout, zkHosts: String, zkPath: String, zkTimeout: Int)

case class Info(frameworkId: String, leader: String, httpConfig: HttpConfig, eventSubscriber: EventSubscriber, marathonConfig: MarathonConfig, name: String, version: String, zookeeperConfig: ZookeeperConfig)