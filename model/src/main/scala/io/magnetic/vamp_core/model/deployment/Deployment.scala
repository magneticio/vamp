package io.magnetic.vamp_core.model.deployment

import io.magnetic.vamp_core.model.artifact.{Routing, Scale, Breed, Sla}


case class Deployment(id: String, clusters: List[Cluster])

case class Cluster(name: String, services: List[Service], sla: Option[Sla])

case class Service(breed: Breed, scale: Option[Scale], routing: Option[Routing])
