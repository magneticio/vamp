package io.magnetic.marathon.client.api

case class Task(appId: String, healthCheckResults: List[AnyRef], host: String, id: String, ports: List[Int], servicePorts: List[Int], stagedAt: String, startedAt: String, version: String)