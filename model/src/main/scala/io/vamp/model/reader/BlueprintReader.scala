package io.vamp.model.reader

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.BlueprintTraitValidator

import scala.language.postfixOps

trait AbstractBlueprintReader extends YamlReader[Blueprint] with ReferenceYamlReader[Blueprint] with TraitReader with DialectReader with BlueprintTraitValidator with BlueprintRoutingHelper {

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
                    case Some(_) ⇒ >>("breed", source)
                  }
                case _ ⇒
              }
              expandDialect
              element
            }
          })
          expandDialect
        case _ ⇒
      }
      case _ ⇒
    }
    super.expand
  }

  private def expandDialect(implicit source: YamlSourceReader) = {
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
              Cluster(name, services, processAnonymousRouting(services, RoutingReader.mapping("routing")), sla, dialects)
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
      validateBlueprintGateways(blueprint)
      validateRoutingAnonymousPortMapping(blueprint)

      if (blueprint.clusters.flatMap(_.services).count(_ ⇒ true) == 0) throwException(NoServiceError)

      val breeds = blueprint.clusters.flatMap(_.services.map(_.breed))

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
          if (!cluster.services.exists(_.breed.name == route.path.source))
            throwException(UnresolvedServiceRouteError(cluster, route.path.source))
        }
      }
    }
  }

  protected def validateRouteWeights(blueprint: AbstractBlueprint): Unit = {
    blueprint.clusters.find({ cluster ⇒
      cluster.routing.exists { routing ⇒
        val weights = routing.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.weight)
        weights.exists(_ < 0) || weights.sum > 100
      }
    }).flatMap {
      case cluster ⇒ throwException(RouteWeightError(cluster))
    }
  }

  private def parseService(implicit source: YamlSourceReader): Service =
    Service(BreedReader.readReference(<<![Any]("breed")), environmentVariables(alias = false), ScaleReader.readOptionalReferenceOrAnonymous("scale"), dialects)
}

trait BlueprintRoutingHelper {
  this: NotificationProvider ⇒

  protected def processAnonymousRouting(services: List[AbstractService], routing: List[Gateway]): List[Gateway] = {
    if (routing.exists(_.port.name == Gateway.anonymous)) {
      val ports = services.map(_.breed).flatMap {
        case breed: DefaultBreed ⇒ breed.ports
        case _                   ⇒ Nil
      }
      if (ports.size == 1)
        routing.find(_.port.name == Gateway.anonymous).get.copy(port = Port(ports.head.name, None, None)) :: Nil
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

  protected def validateBlueprintGateways[T <: AbstractBlueprint]: T ⇒ T = validateStickiness[T] andThen validateFilterConditions[T]

  private def validateStickiness[T <: AbstractBlueprint]: T ⇒ T = { blueprint ⇒
    blueprint.clusters.foreach { cluster ⇒
      cluster.services.foreach { service ⇒
        service.breed match {
          case breed: DefaultBreed ⇒ breed.ports.foreach { port ⇒
            if (port.`type` != Port.Type.Http && cluster.routingBy(port.name).flatMap(_.sticky).isDefined) throwException(StickyPortTypeError(port))

            blueprint.gateways.foreach { gateway ⇒
              gateway.routeBy(cluster.name :: port.name :: Nil) match {
                case Some(route) ⇒ if (gateway.port.`type` != Port.Type.Http && gateway.sticky.isDefined) throwException(StickyPortTypeError(gateway.port.copy(name = route.path.source)))
                case _           ⇒
              }
            }
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
                    case route: DefaultRoute ⇒ route.filters.foreach(filter ⇒ if (DefaultFilter.isHttp(filter)) throwException(FilterPortTypeError(port, filter)))
                    case _                   ⇒
                  }
                  case None ⇒
                }
              }

              blueprint.gateways.foreach { gateway ⇒
                gateway.routeBy(cluster.name :: port.name :: Nil) match {
                  case Some(route: DefaultRoute) ⇒ if (gateway.port.`type` != Port.Type.Http) route.filters.foreach(filter ⇒ if (DefaultFilter.isHttp(filter)) throwException(FilterPortTypeError(gateway.port.copy(name = route.path.source), filter)))
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
}

object ScaleReader extends YamlReader[Scale] with WeakReferenceYamlReader[Scale] {

  override protected def createReference(implicit source: YamlSourceReader): Scale = ScaleReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Scale = DefaultScale(name, <<![Double]("cpu"), <<![Double]("memory"), <<![Int]("instances"))
}

trait GatewayMappingReader[T <: Artifact] extends YamlReader[List[T]] {

  import YamlSourceReader._

  def mapping(entry: String)(implicit source: YamlSourceReader): List[T] = <<?[YamlSourceReader](entry) match {
    case Some(yaml) ⇒ read(yaml)
    case None       ⇒ Nil
  }

  protected def parse(implicit source: YamlSourceReader): List[T] = source.pull().keySet.map { port ⇒
    val yaml = <<![YamlSourceReader](port :: Nil)
    if (yaml.find[Any]("port").isDefined) throwException(UnexpectedElement(Map[String, Any](name -> "port"), ""))
    >>("port", port)(yaml)
    reader.readAnonymous(yaml)
  } toList

  protected def reader: AnonymousYamlReader[T]
}

object BlueprintGatewayReader extends GatewayMappingReader[Gateway] {

  protected val reader = GatewayReader

  override protected def expand(implicit source: YamlSourceReader) = {
    source.pull().keySet.map { port ⇒
      <<![Any](port :: Nil) match {
        case route: String ⇒ >>(port :: "routes", route)
        case _             ⇒
      }
    }

    super.expand
  }
}

object RoutingReader extends GatewayMappingReader[Gateway] {

  protected val reader = ClusterGatewayReader

  override protected def expand(implicit source: YamlSourceReader) = {
    if (source.pull({ entry ⇒ entry == "sticky" || entry == "routes" }).nonEmpty) >>(Gateway.anonymous, <<-())
    super.expand
  }
}