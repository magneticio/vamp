package io.vamp.core.cli

import io.vamp.core.model.artifact._

import scala.language.implicitConversions

class Deserialization {

  case class BlueprintSer(name: String, clusters: Map[String, ClusterSer], endpoints: Option[Map[String, String]], environmentVariables: Option[Map[String, String]])

  case class ClusterSer(services: List[ServiceSer], sla: Option[Map[String, _]])

  case class ServiceSer(breed: BreedSer, scale: Option[ScaleSer], routing: Option[RoutingSer])

  case class ScaleSer(cpu: Double, memory: Double, instances: Int)

  case class BreedSer(name: String, deployable: String, ports: Option[Map[String, String]], environmentVariables: Option[Map[String, String]], constants: Option[Map[String, String]], dependencies: Map[String, BreedSer])

  case class RoutingSer(weight: Int, filters: List[String])

  case class DeploymentSer(name: String, clusters: Map[String, ClusterSer], endpoints: Option[Map[String, String]], environmentVariables: Option[Map[String, String]])


  // TODO SLA & Escalation support not implemented
  case class SlaSimplified(name: String, `type`: Option[String], escalations: List[EscalationSimplified], parameters: Map[String, Any])

  case class EscalationSimplified(name: String, `type`: String, parameters: Map[String, Any])


  implicit def mapToPortList(m: Option[Map[String, String]]): List[Port] = m match {
    case Some(p) => p.map(port => Port(name = port._1, value = Some(port._2), alias = None)).toList
    case None => List.empty
  }

  implicit def mapToEnvironmentVariables(m: Option[Map[String, String]]): List[EnvironmentVariable] = m match {
    case Some(e) => e.map(env => EnvironmentVariable(name = env._1, value = Some(env._2), alias = None)).toList
    case None => List.empty
  }

  implicit def mapToConstants(m: Option[Map[String, String]]): List[Constant] = m match {
    case Some(c) => c.map(con => Constant(name = con._1, value = Some(con._2), alias = None)).toList
    case None => List.empty
  }

  implicit def breedSer2DefaultBreed(b: BreedSer): DefaultBreed = DefaultBreed(
    name = b.name,
    deployable = Deployable(b.deployable),
    ports = b.ports,
    environmentVariables = b.environmentVariables,
    constants = b.constants,
    dependencies = b.dependencies.map(dep => dep._1 -> breedSer2DefaultBreed(dep._2))
  )

  implicit def scaleSerToScale(s: ScaleSer): DefaultScale =
    DefaultScale(name = "", cpu = s.cpu, memory = s.memory, instances = s.instances)


  implicit def routingSer2DefaultRouting(r: RoutingSer): DefaultRouting =
    DefaultRouting(name = "", weight = Some(r.weight), filters = r.filters.map(c => DefaultFilter(name = "", condition = c)))


  implicit def serviceSer2Service(s: ServiceSer): Service =
    Service(breed = s.breed, scale = s.scale.map(scaleSerToScale), routing = s.routing.map(routingSer2DefaultRouting))


  implicit def clusterSer2Cluster(m: Map[String, ClusterSer]): List[Cluster] =
    m.map(c =>
      Cluster(name = c._1, services = c._2.services.map(serviceSer2Service), sla = None) //TODO implement SLA
    ).toList


  implicit def blueprintSer2DefaultBlueprint(b: BlueprintSer): DefaultBlueprint =
    DefaultBlueprint(
      name = b.name,
      clusters = b.clusters,
      endpoints = b.endpoints,
      environmentVariables = b.environmentVariables
    )

}
