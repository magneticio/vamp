package io.vamp.core.cli

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import io.vamp.core.model.artifact.DeploymentService._
import io.vamp.core.model.artifact._

import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, postfixOps}


class Deserialization {


  case class BlueprintSerialized(name: String, clusters: Map[String, ClusterSerialized], endpoints: Option[Map[String, String]], environmentVariables: Option[Map[String, String]])

  case class ClusterSerialized(services: List[ServiceSerialized], sla: Option[Map[String, _]])

  case class ServiceSerialized(breed: DefaultBreedSerialized, scale: Option[ScaleSerialized], routing: Option[RoutingSerialized])

  case class ScaleSerialized(cpu: Double, memory: Double, instances: Int)

  case class DefaultBreedSerialized(name: String, deployable: String, ports: Option[Map[String, String]], environmentVariables: Option[Map[String, String]], constants: Option[Map[String, String]], dependencies: Map[String, BreedSerialized])

  case class BreedSerialized(name: String)

  case class RoutingSerialized(weight: Int, filters: List[String])

  case class DeploymentSerialized(name: String, clusters: Map[String, DeploymentClusterSerialized], endpoints: Option[Map[String, String]], environmentVariables: Option[Map[String, String]], hosts: Option[Map[String, String]], constants: Option[Map[String, String]], ports: Option[Map[String, String]])

  case class DeploymentClusterSerialized(services: List[DeploymentServiceSerialized], sla: Option[Map[String, _]])

  case class DeploymentServiceSerialized(breed: DefaultBreedSerialized, scale: Option[ScaleSerialized], routing: Option[RoutingSerialized], state: Option[Map[String, String]], servers: List[Map[String, _]])

  implicit def mapToHostList(m: Option[Map[String, String]]): List[Host] = m match {
    case Some(h) => h.map(host => Host(name = host._1, value = Some(host._2))).toList
    case None => List.empty
  }

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

  implicit def mapToServerPorts(m: Option[Map[String, BigInt]]): Map[Int, Int] = m match {
    case Some(p) => p.map(port => port._1.toInt -> port._2.toInt)
    case None => Map.empty
  }

  implicit def breedSerialized2DefaultBreed(b: DefaultBreedSerialized): DefaultBreed = DefaultBreed(
    name = b.name,
    deployable = Deployable(b.deployable),
    ports = b.ports,
    environmentVariables = b.environmentVariables,
    constants = b.constants,
    dependencies = b.dependencies.map(dep => dep._1 -> BreedReference(name=dep._2.name))
  )

  implicit def scaleSerializedToScale(s: ScaleSerialized): DefaultScale =
    DefaultScale(name = "", cpu = s.cpu, memory = s.memory, instances = s.instances)


  implicit def routingSerialized2DefaultRouting(r: RoutingSerialized): DefaultRouting =
    DefaultRouting(name = "", weight = Some(r.weight), filters = r.filters.map(c => DefaultFilter(name = "", condition = c)))


  implicit def serviceSerialized2Service(s: ServiceSerialized): Service =
    Service(breed = s.breed, scale = s.scale.map(scaleSerializedToScale), routing = s.routing.map(routingSerialized2DefaultRouting))


  implicit def clusterSerialized2Cluster(m: Map[String, ClusterSerialized]): List[Cluster] =
    m.map(c =>
      Cluster(name = c._1, services = c._2.services.map(serviceSerialized2Service), sla = mapToSla(c._2.sla))
    ).toList


  implicit def deploymentServerSerialized2DeploymentServer(m: Map[String, _]): DeploymentServer =
    DeploymentServer(name = m.getOrElse("name", "").asInstanceOf[String],
      host = m.getOrElse("host", "").asInstanceOf[String],
      ports = mapToServerPorts(m.get("ports").asInstanceOf[Option[Map[String, BigInt]]]),
      deployed = m.getOrElse("deployed", false).asInstanceOf[Boolean]
    )


  implicit def deploymentStateSerialized2State(s: Map[String, String]): State = {
    val timestamp = s.get("started_at").get
    val dateTime = OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_INSTANT) //TODO this will fail
    s.get("name") match {
      case Some("ready_for_deployment") => ReadyForDeployment(startedAt = dateTime)
      case Some("deployed") => Deployed(startedAt = dateTime)
      case Some("ready_for_undeployment") => ReadyForUndeployment(startedAt = dateTime)
      case Some("error") => Deployed()
      case _ => Deployed()
    }
  }


  implicit def deploymentServiceSerialized2DeploymentService(s: DeploymentServiceSerialized): DeploymentService =
    DeploymentService(
      breed = s.breed,
      scale = s.scale.map(scaleSerializedToScale),
      routing = s.routing.map(routingSerialized2DefaultRouting),
      servers = s.servers.map(deploymentServerSerialized2DeploymentServer),
      state = Deployed() //TODO add the correct state //s.state.map(deploymentStateSerialized2State).get
    )


  implicit def deploymentClusterSerialized2DeploymentCluster(m: Map[String, DeploymentClusterSerialized]): List[DeploymentCluster] =
    m.map(c =>
      DeploymentCluster(name = c._1, services = c._2.services.map(deploymentServiceSerialized2DeploymentService), sla = mapToSla(c._2.sla))
    ).toList

  implicit def mapToSla(slaOption: Option[Map[String, _]]): Option[Sla] = slaOption match {
    case Some(sla) =>
      val name = sla.getOrElse("name", "").asInstanceOf[String]
      sla.getOrElse("type", "").asInstanceOf[String] match {
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
        case other =>
          val escalations = sla.getOrElse("escalations", List.empty).asInstanceOf[List[Map[String, _]]]
          val params = sla.getOrElse("parameters", List.empty).asInstanceOf[Map[String, Any]]
          Some(GenericSla(name = name, `type` = other, parameters = params, escalations = escalations.map(map2Escalation)))
      }
    case None => None
  }

  implicit def map2Escalation(esc: Map[String, _]): Escalation = {
    val name = esc.getOrElse("name", "").asInstanceOf[String]
    esc.getOrElse("type", "").asInstanceOf[String] match {
      case "to_all" =>
        val escalations = esc.getOrElse("escalations", List.empty).asInstanceOf[List[Map[String, _]]]
        ToAllEscalation(name = name, escalations = escalations.map(map2Escalation))
      case "to_one" =>
        val escalations = esc.getOrElse("escalations", List.empty).asInstanceOf[List[Map[String, _]]]
        ToOneEscalation(name = name, escalations = escalations.map(map2Escalation))
      case "scale_instances" =>
        ScaleInstancesEscalation(name = name,
          minimum = esc.getOrElse("minimum", 0).asInstanceOf[BigInt].toInt,
          maximum = esc.getOrElse("maximum", 0).asInstanceOf[BigInt].toInt,
          scaleBy = esc.getOrElse("scale_by", 0).asInstanceOf[BigInt].toInt,
          targetCluster = esc.get("target_cluster").asInstanceOf[Option[String]])
      case "scale_cpu" =>
        ScaleCpuEscalation(name = name,
          minimum = esc.getOrElse("minimum", 0d).asInstanceOf[BigDecimal].toDouble,
          maximum = esc.getOrElse("maximum", 0d).asInstanceOf[BigDecimal].toDouble,
          scaleBy = esc.getOrElse("scale_by", 0d).asInstanceOf[BigDecimal].toDouble,
          targetCluster = esc.get("target_cluster").asInstanceOf[Option[String]])
      case "scale_memory" =>
        ScaleMemoryEscalation(name = name,
          minimum = esc.getOrElse("minimum", 0d).asInstanceOf[BigDecimal].toDouble,
          maximum = esc.getOrElse("maximum", 0d).asInstanceOf[BigDecimal].toDouble,
          scaleBy = esc.getOrElse("scale_by", 0d).asInstanceOf[BigDecimal].toDouble,
          targetCluster = esc.get("target_cluster").asInstanceOf[Option[String]])
      case other =>
        val params = esc.getOrElse("parameters", List.empty).asInstanceOf[Map[String, Any]]
        GenericEscalation(name = name, `type` = other, parameters = params)
    }
  }

  implicit def blueprintSerialized2DefaultBlueprint(b: BlueprintSerialized): DefaultBlueprint =
    DefaultBlueprint(
      name = b.name,
      clusters = b.clusters,
      endpoints = b.endpoints,
      environmentVariables = b.environmentVariables
    )

  implicit def deploymentSerialized2Deployment(dep: DeploymentSerialized): Deployment =
    Deployment(
      name = dep.name,
      clusters = dep.clusters,
      environmentVariables = dep.environmentVariables,
      endpoints = dep.endpoints,
      constants = dep.constants,
      hosts = dep.hosts,
      ports = dep.ports
    )

}
