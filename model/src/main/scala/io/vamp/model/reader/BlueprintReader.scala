package io.vamp.model.reader

import io.vamp.common.notification.{ Notification, NotificationErrorException, NotificationProvider }
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.{ BlueprintTraitValidator, BreedTraitValueValidator }

import scala.language.postfixOps
import scala.util.Try

trait AbstractBlueprintReader extends YamlReader[Blueprint]
    with ReferenceYamlReader[Blueprint]
    with TraitReader
    with ArgumentReader
    with DialectReader
    with BreedTraitValueValidator
    with BlueprintTraitValidator
    with GatewayRouteValidation
    with BlueprintGatewayHelper {

  override def readReference: PartialFunction[Any, Blueprint] = {
    case string: String ⇒ BlueprintReference(string)
    case yaml: YamlSourceReader ⇒
      implicit val source = yaml
      if (source.size > 1) read(source) else BlueprintReference(name)
  }

  override protected def expand(implicit source: YamlSourceReader) = {
    <<?[YamlSourceReader]("clusters") match {
      case Some(yaml) ⇒ yaml.pull().map {
        case (name: String, breed: String) ⇒ >>("clusters" :: name :: "services", List(YamlSourceReader("breed" → breed)))
        case (name: String, list: List[_]) ⇒ >>("clusters" :: name :: "services", list)
        case _                             ⇒
      }
      case _ ⇒
    }
    <<?[YamlSourceReader]("clusters") match {
      case Some(yaml) ⇒ yaml.pull().map {
        case (name: String, cluster: YamlSourceReader) ⇒
          implicit val source = cluster
          <<?[Any]("services") match {
            case None                ⇒ >>("services", List(<<-("sla", "gateways")))
            case Some(list: List[_]) ⇒
            case Some(breed: String) ⇒ >>("services", List(YamlSourceReader("breed" → breed)))
            case Some(m)             ⇒ >>("services", List(m))
          }
          >>("services", <<![List[_]]("services").map { element ⇒
            if (element.isInstanceOf[String]) {
              YamlSourceReader("breed" → YamlSourceReader("reference" → element))
            }
            else {
              implicit val source = element.asInstanceOf[YamlSourceReader]
              <<?[Any]("breed") match {
                case None ⇒
                  <<?[Any]("name") match {
                    case None ⇒ hasReference match {
                      case None      ⇒
                      case Some(ref) ⇒ >>("breed", ref)
                    }
                    case Some(_) ⇒ >>("breed", <<-())
                  }
                case _ ⇒
              }
              expandArguments()
              expandDialect()
              element
            }
          })
          expandArguments()
          expandDialect()
        case _ ⇒
      }
      case _ ⇒
    }
    super.expand
  }

  private def expandDialect()(implicit source: YamlSourceReader) = {
    <<?[Any]("dialects") match {
      case None ⇒ >>("dialects", YamlSourceReader(dialectValues.map { case (k, v) ⇒ k.toString.toLowerCase → v }))
      case _    ⇒
    }
  }

  override def parse(implicit source: YamlSourceReader): Blueprint = {
    val clusters = <<?[YamlSourceReader]("clusters") match {
      case None ⇒ List[Cluster]()
      case Some(yaml) ⇒ yaml.pull().collect {
        case (name: String, cluster: YamlSourceReader) ⇒
          implicit val source = cluster
          val sla = SlaReader.readOptionalReferenceOrAnonymous("sla")

          val addHealthChecks = (healthChecks: List[HealthCheck]) ⇒ <<?[List[YamlSourceReader]]("services") match {
            case None ⇒ Cluster(name, metadata, List(), Nil, <<?[String]("network"), sla, dialects)
            case Some(list) ⇒
              val services = list.map(parseService(_))
              Cluster(name, metadata, services, processAnonymousInternalGateways(services, internalGatewayReader.mapping("gateways")), <<?[String]("network"), sla, dialects)
          }

          <<?[YamlSourceReader]("health_checks") match {
            case None        ⇒ addHealthChecks(List())
            case Some(input) ⇒ addHealthChecks(HealthCheckReader.read(input))
          }
      } toList
    }

    val evs = environmentVariables(alias = false, addGroup = true)

    DefaultBlueprint(name, metadata, clusters, BlueprintGatewayReader.mapping("gateways"), evs)
  }

  override protected def validate(bp: Blueprint): Blueprint = bp match {
    case blueprint: BlueprintReference ⇒ blueprint
    case blueprint: DefaultBlueprint ⇒

      blueprint.clusters.foreach(cluster ⇒ validateName(cluster.name))
      validateBlueprintTraitValues(blueprint)

      validateRouteServiceNames(blueprint)
      validateRouteWeights(blueprint)
      validateRouteConditionStrengths(blueprint)
      blueprint.gateways.foreach((validateGatewayRouteWeights andThen validateGatewayRouteConditionStrengths)(_))
      validateBlueprintGateways(blueprint)
      validateInternalGatewayAnonymousPortMapping(blueprint)

      if (blueprint.clusters.flatMap(_.services).count(_ ⇒ true) == 0) throwException(NoServiceError)

      val services = blueprint.clusters.flatMap(_.services)
      val breeds = services.map(_.breed)

      services.foreach(service ⇒ validateArguments(service.arguments))

      validateBreeds(breeds)
      validateServiceEnvironmentVariables(blueprint.clusters.flatMap(_.services))
      validateDependencies(breeds)
      breeds.foreach(BreedReader.validateNonRecursiveDependencies)

      // validates each individual health check linked to a cluster and service
      for {
        cluster ← blueprint.clusters
        service ← cluster.services
        healthCheck ← service.healthChecks
      } yield validateHealthCheck(service, healthCheck)

      blueprint
  }

  /** Validates a healthCheck based on the linked service **/
  protected def validateHealthCheck(service: Service, healthCheck: HealthCheck): Unit = {
    val correctPort = Try(healthCheck.port.toInt).isSuccess || (service.breed match {
      case defaultBreed: DefaultBreed ⇒ defaultBreed.ports.exists(_.name == healthCheck.port)
      case _                          ⇒ true
    })

    if (!correctPort) throwException(UnresolvedPortReferenceError(healthCheck.port))
    if (healthCheck.failures < 0) throwException(NegativeFailuresNumberError(healthCheck.failures))
  }

  protected def validateBreeds(breeds: List[Breed]): Unit = {
    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 ⇒ throwException(NonUniqueBlueprintBreedReferenceError(name))
    }
  }

  protected def validateServiceEnvironmentVariables(services: List[Service]) = {
    services.foreach { service ⇒
      validateEnvironmentVariablesAgainstBreed(service.environmentVariables, service.breed)
    }
  }

  protected def validateDependencies(breeds: List[Breed]): Unit = {
    breeds.flatMap({
      case breed: DefaultBreed ⇒ breed.dependencies.map((breed, _))
      case _                   ⇒ List()
    }).find({
      case (breed, dependency) ⇒ !breeds.exists(_.name == dependency._2.name)
    }).flatMap {
      case (breed, dependency) ⇒ throwException(UnresolvedBreedDependencyError(breed, dependency))
    }
  }

  protected def validateRouteServiceNames(blueprint: DefaultBlueprint): Unit = {
    blueprint.clusters.foreach { cluster ⇒
      cluster.gateways.foreach { gateways ⇒
        gateways.routes.foreach { route ⇒
          if (!cluster.services.exists(_.breed.name == route.path.normalized))
            throwException(UnresolvedServiceRouteError(cluster, route.path.source))
        }
      }
    }
  }

  protected def validateRouteWeights(blueprint: AbstractBlueprint): Unit = {
    blueprint.clusters.find({ cluster ⇒
      cluster.gateways.exists { gateways ⇒
        val weights = gateways.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.weight)
        weights.exists(_.value < 0) || weights.map(_.value).sum > 100
      }
    }).flatMap { cluster ⇒ throwException(RouteWeightError(cluster)) }
  }

  protected def validateRouteConditionStrengths(blueprint: AbstractBlueprint): Unit = {
    blueprint.clusters.find({ cluster ⇒
      cluster.gateways.exists { gateways ⇒
        val strength = gateways.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.conditionStrength)
        strength.exists(_.value < 0) || strength.exists(_.value > 100)
      }
    }).flatMap { cluster ⇒ throwException(RouteConditionStrengthError(cluster)) }
  }

  private def parseService(implicit source: YamlSourceReader): Service = {
    Service(
      BreedReader.readReference(<<![Any]("breed")),
      environmentVariables(alias = false),
      ScaleReader.readOptionalReferenceOrAnonymous("scale"),
      arguments(),
      HealthCheckReader.read,
      <<?[String]("network"),
      dialects,
      ServiceHealthReader.read)
  }
}

trait BlueprintGatewayHelper {
  this: NotificationProvider ⇒

  protected def processAnonymousInternalGateways(services: List[AbstractService], gateways: List[Gateway]): List[Gateway] = {
    if (gateways.exists(_.port.name == Gateway.anonymous)) {
      val ports = services.map(_.breed).flatMap({
        case breed: DefaultBreed ⇒ breed.ports.map(_.name)
        case _                   ⇒ Nil
      }).toSet
      if (ports.size == 1)
        gateways.find(_.port.name == Gateway.anonymous).get.copy(port = Port(ports.head, None, None)) :: Nil
      else gateways
    }
    else gateways
  }

  protected def validateInternalGatewayAnonymousPortMapping[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.clusters.foreach { cluster ⇒
      if (cluster.gatewayBy(Gateway.anonymous).isDefined) {
        cluster.services.foreach { service ⇒
          service.breed match {
            case breed: DefaultBreed ⇒ if (breed.ports.size > 1) throwException(IllegalAnonymousRoutingPortMappingError(breed))
            case _                   ⇒
          }
        }
      }
    }
    blueprint
  }

  protected def validateBlueprintGateways[T <: AbstractBlueprint]: T ⇒ T =
    validateStickiness[T] andThen validateRouteCondition[T] andThen validateGatewayPorts[T] andThen validateInternalGatewayPorts[T]

  private def validateStickiness[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.clusters.foreach { cluster ⇒
      cluster.services.foreach { service ⇒
        service.breed match {
          case breed: DefaultBreed ⇒ breed.ports.foreach { port ⇒
            if (port.`type` != Port.Type.Http && cluster.gatewayBy(port.name).flatMap(_.sticky).isDefined) throwException(StickyPortTypeError(port))
          }
          case _ ⇒
        }
      }
    }
    blueprint
  }

  private def validateRouteCondition[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.clusters.foreach { cluster ⇒
      cluster.services.foreach { service ⇒
        service.breed match {
          case breed: DefaultBreed ⇒
            breed.ports.foreach { port ⇒
              if (port.`type` != Port.Type.Http) {
                cluster.gatewayBy(port.name) match {
                  case Some(gateways) ⇒ gateways.routes.foreach {
                    case route: DefaultRoute ⇒ if (route.definedCondition) throwException(ConditionPortTypeError(port, route.condition.get))
                    case _                   ⇒
                  }
                  case None ⇒
                }
              }

              blueprint.gateways.foreach { gateway ⇒
                gateway.routeBy(cluster.name :: port.name :: Nil) match {
                  case Some(route: DefaultRoute) ⇒ if (gateway.port.`type` != Port.Type.Http) if (route.definedCondition) throwException(ConditionPortTypeError(gateway.port.copy(name = route.path.source), route.condition.get))
                  case _                         ⇒
                }
              }
            }
          case _ ⇒
        }
      }
    }
    blueprint
  }

  private def validateGatewayPorts[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.gateways.groupBy(_.port.number).collect { case (port, list) if list.size > 1 ⇒ throwException(DuplicateGatewayPortError(port)) }
    blueprint
  }

  private def validateInternalGatewayPorts[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    val breeds = blueprint.clusters.flatMap(_.services).map(_.breed)

    if (breeds.forall(_.isInstanceOf[DefaultBreed])) {
      val ports = breeds.flatMap {
        case breed: DefaultBreed ⇒ breed.ports.map(_.name)
        case _                   ⇒ Nil
      } distinct

      blueprint.clusters.flatMap(_.gateways).map(_.port).foreach { port ⇒
        if (!(port.name.isEmpty || ports.contains(port.name)))
          throwException(UnresolvedGatewayPortError(port.name, ""))
      }
    }

    blueprint
  }

  protected def internalGatewayReader: GatewayMappingReader[Gateway] = new InternalGatewayReader(acceptPort = false)
}

object BlueprintReader extends AbstractBlueprintReader {

  override protected def validate(blueprint: Blueprint) = {
    super.validate(blueprint)
    blueprint match {
      case bp: AbstractBlueprint ⇒ validateScaleEscalations(bp)
      case _                     ⇒
    }
    blueprint
  }

  def validateScaleEscalations(blueprint: AbstractBlueprint): Unit = {
    blueprint.clusters.foreach { cluster ⇒
      cluster.sla match {
        case None ⇒
        case Some(s) ⇒ s.escalations.foreach {
          case escalation: ScaleEscalation[_] ⇒ escalation.targetCluster match {
            case None ⇒
            case Some(clusterName) ⇒ blueprint.clusters.find(_.name == clusterName) match {
              case None    ⇒ throwException(UnresolvedScaleEscalationTargetCluster(cluster, clusterName))
              case Some(_) ⇒
            }
          }
          case _ ⇒
        }
      }
    }
  }
}

object DeploymentBlueprintReader extends AbstractBlueprintReader {

  override protected def validateDependencies(breeds: List[Breed]): Unit = {}

  override def reportException(notification: Notification): Exception = NotificationErrorException(notification, message(notification))

  override protected def consistent(blueprint: Blueprint)(implicit source: YamlSourceReader): Blueprint = {
    <<?[String](Artifact.kind) match {
      case Some(kind) if kind == Blueprint.kind || kind == Deployment.kind ⇒ blueprint
      case _ ⇒ super.consistent(blueprint)
    }
  }
}

object ScaleReader extends YamlReader[Scale] with WeakReferenceYamlReader[Scale] {

  override protected def createReference(implicit source: YamlSourceReader): Scale = ScaleReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Scale = {
    DefaultScale(name, metadata, <<![Quantity]("cpu"), <<![MegaByte]("memory"), <<?[Int]("instances").getOrElse(1))
  }
}

object HealthCheckReader extends YamlReader[List[HealthCheck]] {

  def healthCheck(implicit source: YamlSourceReader): HealthCheck =
    HealthCheck(
      <<?[String]("path").getOrElse("/"),
      <<![String]("port"),
      Time.of(<<![String]("initial_delay")),
      Time.of(<<![String]("timeout")),
      Time.of(<<![String]("interval")),
      <<![Int]("failures"),
      <<?[String]("protocol").getOrElse("HTTP"))

  override protected def parse(implicit source: YamlSourceReader): List[HealthCheck] =
    <<?[List[YamlSourceReader]]("health_checks")
      .map(_.map(healthCheck(_)))
      .getOrElse(List.empty[HealthCheck])

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {
    expandToList("health_checks")
    source
  }

}