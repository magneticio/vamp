package io.magnetic.vamp_core.model.reader

import io.magnetic.vamp_core.model._
import io.magnetic.vamp_core.model.notification.{NonUniqueBlueprintBreedReferenceError, UnresolvedBreedDependencyError, UnresolvedEndpointPortError, UnresolvedParameterError}

import scala.language.postfixOps

object BlueprintReader extends YamlReader[Blueprint] {

  override protected def expand(implicit source: YamlObject) = {
    <<?[YamlObject]("clusters") match {
      case None =>
      case Some(map) => map.map {
        case (name: String, breed: String) => >>("clusters" :: name :: "services", List(new YamlObject() += ("breed" -> breed)))
        case (name: String, cluster: collection.Map[_, _]) =>
          implicit val source = cluster.asInstanceOf[YamlObject]
          <<?[Any]("services") match {
            case None => <<?[Any]("breed") match {
              case None => <<?[Any]("name") match {
                case None =>
                case Some(_) => >>("services", List(new YamlObject() += ("breed" -> source)))
              }
              case Some(breed) => >>("services", List(new YamlObject() += ("breed" -> breed)))
            }
            case Some(breed: String) => >>("services", List(new YamlObject() += ("breed" -> breed)))
            case Some(list) =>
              >>("services", list.asInstanceOf[List[_]].map {
                case breed: String => new YamlObject() += ("breed" -> breed)
                case map: collection.Map[_, _] =>
                  implicit val source = map.asInstanceOf[YamlObject]
                  <<?[Any]("routing") match {
                    case None =>
                    case Some(s: String) =>
                    case Some(_) => expandToList("routing" :: "filters")
                  }
                  source
              })
          }
      }
    }

    super.expand
  }

  override def parse(implicit source: YamlObject): Blueprint = {

    val clusters = <<?[YamlObject]("clusters") match {
      case None => List[Cluster]()
      case Some(map) => map.map({
        case (name: String, cluster: collection.Map[_, _]) =>
          implicit val source = cluster.asInstanceOf[YamlObject]
          val sla = SlaReader.readOptionalReference("sla")

          <<?[List[YamlObject]]("services") match {
            case None => Cluster(name, List(), sla)
            case Some(list) => Cluster(name, list.map(service(_)), sla)
          }
      }).toList
    }

    Blueprint(name, clusters, traitNameMapping("endpoints"), traitNameMapping("parameters"))
  }

  override protected def validate(blueprint: Blueprint): Blueprint = {
    blueprint.endpoints.find({
      case (Trait.Name(Some(scope), Some(Trait.Name.Group.Ports), port), _) =>
        blueprint.clusters.find(_.name == scope) match {
          case None => true
          case Some(cluster) => cluster.services.exists(_.breed match {
            case _: DefaultBreed => true
            case _ => false
          }) && cluster.services.find({
            service => service.breed match {
              case breed: DefaultBreed => breed.ports.exists(_.name.toString == port)
              case _ => false
            }
          }).isEmpty
        }
      case _ => true
    }).flatMap {
      case (name, value) => error(UnresolvedEndpointPortError(name, value))
    }

    blueprint.parameters.find({
      case (Trait.Name(Some(scope), Some(group), port), _) =>
        blueprint.clusters.find(_.name == scope) match {
          case None => true
          case Some(cluster) => cluster.services.exists(_.breed match {
            case _: DefaultBreed => true
            case _ => false
          }) && cluster.services.find({
            service => service.breed match {
              case breed: DefaultBreed => breed.inTraits.exists(_.name.toString == port)
              case _ => false
            }
          }).isEmpty
        }
      case _ => true
    }).flatMap {
      case (name, value) => error(UnresolvedParameterError(name, value))
    }

    val breeds = blueprint.clusters.flatMap(_.services.map(_.breed))

    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 => error(NonUniqueBlueprintBreedReferenceError(name))
    }

    breeds.flatMap({
      case breed: DefaultBreed => breed.dependencies.map((breed, _))
      case _ => List()
    }).find({
      case (breed, dependency) => breeds.find(_.name == dependency._2.name).isEmpty
    }).flatMap {
      case (breed, dependency) => error(UnresolvedBreedDependencyError(breed, dependency))
    }

    blueprint
  }

  private def service(implicit source: YamlObject): Service =
    Service(BreedReader.readReference(<<![Any]("breed")), ScaleReader.readOptionalReference("scale"), RoutingReader.readOptionalReference("routing"))

  protected def traitNameMapping(path: YamlPath)(implicit source: YamlObject): Map[Trait.Name, String] = <<?[YamlObject](path) match {
    case None => Map()
    case Some(map) => map.map {
      case (name: String, _) =>
        implicit val source = map.asInstanceOf[YamlObject]
        (Trait.Name.asName(name), <<![String](name))
    } toMap
  }
}

object SlaReader extends YamlReader[Sla] with WeakReferenceYamlReader[Sla] {

  override protected def validateEitherReferenceOrAnonymous(implicit source: YamlObject): YamlObject = {
    if (source.filterKeys(k => k != "name" && k != "escalations").nonEmpty) super.validate
    source
  }

  override protected def createReference(implicit source: YamlObject): Sla = SlaReference(reference, escalations)

  override protected def createAnonymous(implicit source: YamlObject): Sla = AnonymousSla(`type`, escalations, parameters)

  protected def escalations(implicit source: YamlObject): List[Escalation] = <<?[YamlList]("escalations") match {
    case None => List[Escalation]()
    case Some(list: YamlList) => list.map {
      EscalationReader.readReference
    }
  }

  override protected def parameters(implicit source: YamlObject): Map[String, Any] = super.parameters.filterKeys(_ != "escalations")
}

object EscalationReader extends YamlReader[Escalation] with WeakReferenceYamlReader[Escalation] {

  override protected def createReference(implicit source: YamlObject): Escalation = EscalationReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Escalation = AnonymousEscalation(`type`, parameters)
}

object ScaleReader extends YamlReader[Scale] with WeakReferenceYamlReader[Scale] {

  override protected def createReference(implicit source: YamlObject): Scale = ScaleReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Scale = AnonymousScale(<<![Double]("cpu"), <<![Double]("memory"), <<![Int]("instances"))
}

object RoutingReader extends YamlReader[Routing] with WeakReferenceYamlReader[Routing] {

  override protected def createReference(implicit source: YamlObject): Routing = RoutingReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Routing = AnonymousRouting(<<?[Int]("weight"), filters)

  protected def filters(implicit source: YamlObject): List[Filter] = <<?[YamlList]("filters") match {
    case None => List[Filter]()
    case Some(list: YamlList) => list.map {
      FilterReader.readReference
    }
  }
}

object FilterReader extends YamlReader[Filter] with WeakReferenceYamlReader[Filter] {

  override protected def createReference(implicit source: YamlObject): Filter = FilterReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Filter = AnonymousFilter(<<![String]("condition"))
}
