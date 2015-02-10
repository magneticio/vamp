package io.magnetic.marathon.client.api

case class Version(host: String, id: String, ports: List[Int], stagedAt: String, startedAt: String, version: String)