package io.magnetic.marathon.client.api

case class Http_config(assets_path: AnyRef, http_port: Int, https_port: Int)

case class Event_subscriber(`type`: String, http_endpoints: List[AnyRef])

case class Marathon_config(checkpoint: Boolean, executor: String, failover_timeout: Int, ha: Boolean, hostname: String, local_port_max: Int, local_port_min: Int, master: String, mesos_role: AnyRef, mesos_user: String, reconciliation_initial_delay: Int, reconciliation_interval: Int, task_launch_timeout: Int)

case class Zk_future_timeout(duration: Int)

case class Zookeeper_config(zk: String, zk_future_timeout: Zk_future_timeout, zk_hosts: String, zk_path: String, zk_state: String, zk_timeout: Int)

case class Info(frameworkId: String, leader: String, http_config: Http_config, event_subscriber: Event_subscriber, marathon_config: Marathon_config, name: String, version: String, zookeeper_config: Zookeeper_config)