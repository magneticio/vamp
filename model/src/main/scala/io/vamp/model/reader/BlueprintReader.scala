package io.vamp.model.reader

import io.vamp.common.notification.{ Notification, NotificationErrorException, NotificationProvider }
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.BlueprintTraitValidator

import scala.language.postfixOps

trait AbstractBlueprintReader extends YamlReader[Blueprint]
    with ReferenceYamlReader[Blueprint]
    with TraitReader
    with ArgumentReader
    with DialectReader
    with BlueprintTraitValidator
    with BlueprintRoutingHelper {

  override def readReference: PartialFunction[Any, Blueprint] = {
    case string: String ⇒ BlueprintReference(string)
    case yaml: YamlSourceReader ⇒
      implicit val source = yaml
      if (source.size > 1)
        read(source)
      else
        BlueprintReference(name)
    case _ ⇒ throwException(UnexpectedInnerElementError("/", classOf[YamlSourceReader]))
  }

  override protected def expand(implicit source: YamlSourceReader) = {
    <<?[YamlSourceReader]("clusters") match {
      case Some(yaml) ⇒ yaml.pull().map {
        case (name: String, breed: String) ⇒ >>("clusters" :: name :: "services", List(YamlSourceReader("breed" -> breed)))
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
            case None                ⇒ >>("services", List(<<-("sla", "routing")))
            case Some(list: List[_]) ⇒
            case Some(breed: String) ⇒ >>("services", List(YamlSourceReader("breed" -> breed)))
            case Some(m)             ⇒ >>("services", List(m))
          }
          >>("services", <<![List[_]]("services").map { element ⇒
            if (element.isInstanceOf[String]) {
              YamlSourceReader("breed" -> YamlSourceReader("reference" -> element))
            } else {
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
      case None ⇒ >>("dialects", YamlSourceReader(dialectValues.map { case (k, v) ⇒ k.toString.toLowerCase -> v }))
      case _    ⇒
    }
  }

  override def parse(implicit source: YamlSourceReader): Blueprint = {
    val clusters = <<?[YamlSourceReader]("clusters") match {
      case None ⇒ List[Cluster]()
      case Some(yaml) ⇒ yaml.pull().map {
        case (name: String, cluster: YamlSourceReader) ⇒
          implicit val source = cluster
          val sla = SlaReader.readOptionalReferenceOrAnonymous("sla")

          <<?[List[YamlSourceReader]]("services") match {
            case None ⇒ Cluster(name, List(), Nil, sla, dialects)
            case Some(list) ⇒
              val services = list.map(parseService(_))
              Cluster(name, services, processAnonymousRouting(services, routingReader.mapping("routing")), sla, dialects)
          }
      } toList
    }

    val evs = environmentVariables(alias = false, addGroup = true)

    DefaultBlueprint(name, clusters, BlueprintGatewayReader.mapping("gateways"), evs)
  }

  override protected def validate(bp: Blueprint): Blueprint = bp match {
    case blueprint: BlueprintReference ⇒ blueprint
    case blueprint: DefaultBlueprint ⇒

      blueprint.clusters.foreach(cluster ⇒ validateName(cluster.name))
      validateBlueprintTraitValues(blueprint)

      validateRouteServiceNames(blueprint)
      validateRouteWeights(blueprint)
      validateRouteFilterStrengths(blueprint)
      validateGatewayRouteWeights(blueprint)
      validateGatewayRouteFilterStrengths(blueprint)
      validateBlueprintGateways(blueprint)
      validateRoutingAnonymousPortMapping(blueprint)

      if (blueprint.clusters.flatMap(_.services).count(_ ⇒ true) == 0) throwException(NoServiceError)

      val services = blueprint.clusters.flatMap(_.services)
      val breeds = services.map(_.breed)

      services.foreach(service ⇒ validateArguments(service.arguments))

      validateBreeds(breeds)
      validateServiceEnvironmentVariables(blueprint.clusters.flatMap(_.services))
      validateDependencies(breeds)
      breeds.foreach(BreedReader.validateNonRecursiveDependencies)

      blueprint
  }

  protected def validateBreeds(breeds: List[Breed]): Unit = {
    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 ⇒ throwException(NonUniqueBlueprintBreedReferenceError(name))
    }
  }

  protected def validateServiceEnvironmentVariables(services: List[Service]) = services.foreach { service ⇒
    service.breed match {
      case breed: DefaultBreed ⇒ service.environmentVariables.foreach { environmentVariable ⇒
        if (environmentVariable.value.isEmpty) throwException(MissingEnvironmentVariableError(breed, environmentVariable.name))
        if (!breed.environmentVariables.exists(_.name == environmentVariable.name)) throwException(UnresolvedDependencyInTraitValueError(breed, environmentVariable.name))
      }
      case _ ⇒
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
      cluster.routing.foreach { routing ⇒
        routing.routes.foreach { route ⇒
          if (!cluster.services.exists(_.breed.name == route.path.normalized))
            throwException(UnresolvedServiceRouteError(cluster, route.path.source))
        }
      }
    }
  }

  protected def validateRouteWeights(blueprint: AbstractBlueprint): Unit = {
    blueprint.clusters.find({ cluster ⇒
      cluster.routing.exists { routing ⇒
        val weights = routing.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.weight)
        weights.exists(_.value < 0) || weights.map(_.value).sum > 100
      }
    }).flatMap {
      case cluster ⇒ throwException(RouteWeightError(cluster))
    }
  }

  protected def validateRouteFilterStrengths(blueprint: AbstractBlueprint): Unit = {
    blueprint.clusters.find({ cluster ⇒
      cluster.routing.exists { routing ⇒
        val strength = routing.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.filterStrength)
        strength.exists(_.value < 0) || strength.exists(_.value > 100)
      }
    }).flatMap {
      case cluster ⇒ throwException(RouteFilterStrengthError(cluster))
    }
  }

  protected def validateGatewayRouteWeights(blueprint: AbstractBlueprint): Unit = {
    blueprint.gateways.find({ gateway ⇒
      val weights = gateway.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.weight)
      weights.exists(_.value < 0) || weights.map(_.value).sum > 100
    }).flatMap {
      case gateway ⇒ throwException(GatewayRouteWeightError(gateway))
    }
  }

  protected def validateGatewayRouteFilterStrengths(blueprint: AbstractBlueprint): Unit = {
    blueprint.gateways.find({ gateway ⇒
      val strength = gateway.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.filterStrength)
      strength.exists(_.value < 0) || strength.exists(_.value > 100)
    }).flatMap {
      case gateway ⇒ throwException(GatewayRouteFilterStrengthError(gateway))
    }
  }

  private def parseService(implicit source: YamlSourceReader): Service = {
    Service(BreedReader.readReference(<<![Any]("breed")), environmentVariables(alias = false), ScaleReader.readOptionalReferenceOrAnonymous("scale"), arguments(), dialects)
  }
}

trait BlueprintRoutingHelper {
  this: NotificationProvider ⇒

  protected def processAnonymousRouting(services: List[AbstractService], routing: List[Gateway]): List[Gateway] = {
    if (routing.exists(_.port.name == Gateway.anonymous)) {
      val ports = services.map(_.breed).flatMap({
        case breed: DefaultBreed ⇒ breed.ports.map(_.name)
        case _                   ⇒ Nil
      }).toSet
      if (ports.size == 1)
        routing.find(_.port.name == Gateway.anonymous).get.copy(port = Port(ports.head, None, None)) :: Nil
      else routing
    } else routing
  }

  protected def validateRoutingAnonymousPortMapping[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.clusters.foreach { cluster ⇒
      if (cluster.routingBy(Gateway.anonymous).isDefined) {
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
    validateStickiness[T] andThen validateFilterConditions[T] andThen validateGatewayPorts[T] andThen validateGatewayRoutingPorts[T]

  private def validateStickiness[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.clusters.foreach { cluster ⇒
      cluster.services.foreach { service ⇒
        service.breed match {
          case breed: DefaultBreed ⇒ breed.ports.foreach { port ⇒
            if (port.`type` != Port.Type.Http && cluster.routingBy(port.name).flatMap(_.sticky).isDefined) throwException(StickyPortTypeError(port))
          }
          case _ ⇒
        }
      }
    }
    blueprint
  }

  private def validateFilterConditions[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.clusters.foreach { cluster ⇒
      cluster.services.foreach { service ⇒
        service.breed match {
          case breed: DefaultBreed ⇒
            breed.ports.foreach { port ⇒
              if (port.`type` != Port.Type.Http) {
                cluster.routingBy(port.name) match {
                  case Some(routing) ⇒ routing.routes.foreach {
                    case route: DefaultRoute ⇒ route.filters.foreach(filter ⇒ if (filter.isInstanceOf[DefaultFilter]) throwException(FilterPortTypeError(port, filter)))
                    case _                   ⇒
                  }
                  case None ⇒
                }
              }

              blueprint.gateways.foreach { gateway ⇒
                gateway.routeBy(cluster.name :: port.name :: Nil) match {
                  case Some(route: DefaultRoute) ⇒ if (gateway.port.`type` != Port.Type.Http) route.filters.foreach(filter ⇒ if (filter.isInstanceOf[DefaultFilter]) throwException(FilterPortTypeError(gateway.port.copy(name = route.path.source), filter)))
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

  private def validateGatewayRoutingPorts[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    val breeds = blueprint.clusters.flatMap(_.services).map(_.breed)

    if (breeds.forall(_.isInstanceOf[DefaultBreed])) {
      val ports = breeds.flatMap {
        case breed: DefaultBreed ⇒ breed.ports.map(_.name)
        case _                   ⇒ Nil
      } distinct

      blueprint.clusters.flatMap(_.routing).map(_.port).foreach { port ⇒
        if (!(port.name.isEmpty || ports.contains(port.name)))
          throwException(UnresolvedGatewayPortError(port.name, ""))
      }
    }

    blueprint
  }

  protected def routingReader: GatewayMappingReader[Gateway] = new RoutingReader(acceptPort = false)
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
}

object ScaleReader extends YamlReader[Scale] with WeakReferenceYamlReader[Scale] {

  override protected def createReference(implicit source: YamlSourceReader): Scale = ScaleReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Scale = {
    DefaultScale(name, <<![Quantity]("cpu"), <<![MegaByte]("memory"), <<?[Int]("instances").getOrElse(1))
  }
}
