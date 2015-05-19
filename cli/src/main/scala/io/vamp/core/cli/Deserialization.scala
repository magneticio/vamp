package io.vamp.core.cli

import java.util.concurrent.TimeUnit

import io.vamp.core.model.artifact._

import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, postfixOps}

class Deserialization {

  case class BlueprintSer(name: String, clusters: Map[String, ClusterSer], endpoints: Option[Map[String, String]], environmentVariables: Option[Map[String, String]])

  case class ClusterSer(services: List[ServiceSer], sla: Option[Map[String, _]])

  case class ServiceSer(breed: BreedSer, scale: Option[ScaleSer], routing: Option[RoutingSer])

  case class ScaleSer(cpu: Double, memory: Double, instances: Int)

  case class BreedSer(name: String, deployable: String, ports: Option[Map[String, String]], environmentVariables: Option[Map[String, String]], constants: Option[Map[String, String]], dependencies: Map[String, BreedSer])

  case class RoutingSer(weight: Int, filters: List[String])

  case class DeploymentSer(name: String, clusters: Map[String, ClusterSer], endpoints: Option[Map[String, String]], environmentVariables: Option[Map[String, String]])


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
      Cluster(name = c._1, services = c._2.services.map(serviceSer2Service), sla = mapToSla(c._2.sla))
    ).toList


  implicit def mapToSla(slaOption: Option[Map[String, _]]): Option[Sla] = slaOption match {
    case Some(sla) =>
      val name = sla.getOrElse("name", "").asInstanceOf[String]
      sla.getOrElse("type", "") match {
        case "response_time_sliding_window" =>
          val window: Map[String, Long] = sla.getOrElse("window", Map.empty).asInstanceOf[Map[String, Long]]
          val threshold: Map[String, Long] = sla.getOrElse("threshold", Map.empty).asInstanceOf[Map[String, Long]]
          val escalations = sla.getOrElse("escalations", List.empty).asInstanceOf[List[Map[String, _]]]
          Some(
            ResponseTimeSlidingWindowSla(name = name,
              upper = FiniteDuration(length = window.getOrElse("upper", 0L), unit = TimeUnit.SECONDS),
              lower = FiniteDuration(length = window.getOrElse("lower", 0L), unit = TimeUnit.SECONDS),
              interval = FiniteDuration(length = threshold.getOrElse("interval", 0L), unit = TimeUnit.MILLISECONDS),
              cooldown = FiniteDuration(length = threshold.getOrElse("cooldown", 0L), unit = TimeUnit.MILLISECONDS),
              escalations = escalations.map(map2Escalation))
          )
        case "escalation_only" =>
          val escalations = sla.getOrElse("escalations", List.empty).asInstanceOf[List[Map[String, _]]]
          Some(EscalationOnlySla(name = name, escalations = escalations.map(map2Escalation)))
        case a =>
          None
      }
    case None => None
  }


  implicit def map2Escalation(esc: Map[String, _]): Escalation = {
    val name = esc.getOrElse("name", "").asInstanceOf[String]
    esc.get("type") match {
      case Some("to_all") =>
        val escalations = esc.getOrElse("escalations", List.empty).asInstanceOf[List[Map[String, _]]]
        ToAllEscalation(name = name, escalations = escalations.map(map2Escalation))
      case Some("to_one") =>
        val escalations = esc.getOrElse("escalations", List.empty).asInstanceOf[List[Map[String, _]]]
        ToOneEscalation(name = name, escalations = escalations.map(map2Escalation))
      case Some("scale_instances") =>
        ScaleInstancesEscalation(name = name,
          minimum = esc.getOrElse("minimum", 0).asInstanceOf[BigInt].toInt,
          maximum = esc.getOrElse("maximum", 0).asInstanceOf[BigInt].toInt,
          scaleBy = esc.getOrElse("scale_by", 0).asInstanceOf[BigInt].toInt,
          targetCluster = esc.get("target_cluster").asInstanceOf[Option[String]])
      case Some("scale_cpu") =>
        ScaleCpuEscalation(name = name,
          minimum = esc.getOrElse("minimum", 0d).asInstanceOf[BigDecimal].toDouble,
          maximum = esc.getOrElse("maximum", 0d).asInstanceOf[BigDecimal].toDouble,
          scaleBy = esc.getOrElse("scale_by", 0d).asInstanceOf[BigDecimal].toDouble,
          targetCluster = esc.get("target_cluster").asInstanceOf[Option[String]])
      case Some("scale_memory") =>
        ScaleMemoryEscalation(name = name,
          minimum = esc.getOrElse("minimum", 0d).asInstanceOf[BigDecimal].toDouble,
          maximum = esc.getOrElse("maximum", 0d).asInstanceOf[BigDecimal].toDouble,
          scaleBy = esc.getOrElse("scale_by", 0d).asInstanceOf[BigDecimal].toDouble,
          targetCluster = esc.get("target_cluster").asInstanceOf[Option[String]])
      case _ => throw new ClassCastException("Invalid escalation type")
    }
  }


  implicit def blueprintSer2DefaultBlueprint(b: BlueprintSer): DefaultBlueprint =
    DefaultBlueprint(
      name = b.name,
      clusters = b.clusters,
      endpoints = b.endpoints,
      environmentVariables = b.environmentVariables
    )

}
