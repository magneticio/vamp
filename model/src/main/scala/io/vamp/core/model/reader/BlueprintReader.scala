package io.vamp.core.model.reader

import io.vamp.core.model.artifact._
import io.vamp.core.model.notification._

import scala.language.postfixOps

trait AbstractBlueprintReader extends YamlReader[Blueprint] with ReferenceYamlReader[Blueprint] with TraitReader[Blueprint] {

  override def readReference(any: Any): Blueprint = any match {
    case string: String => BlueprintReference(string)
    case map: collection.Map[_, _] =>
      implicit val source = map.asInstanceOf[YamlObject]
      if (source.size > 1)
        read(source)
      else
        BlueprintReference(name)
    case _ => error(UnexpectedInnerElementError("/", classOf[YamlObject]))
  }

  override protected def expand(implicit source: YamlObject) = {
    <<?[YamlObject]("clusters") match {
      case Some(map) => map.map {
        case (name: String, breed: String) => >>("clusters" :: name :: "services", List(new YamlObject() += ("breed" -> breed)))
        case (name: String, list: List[_]) => >>("clusters" :: name :: "services", list)
        case _ =>
      }
      case _ =>
    }
    <<?[YamlObject]("clusters") match {
      case Some(map) => map.map {
        case (name: String, cluster: collection.Map[_, _]) =>
          implicit val source = cluster.asInstanceOf[YamlObject]
          <<?[Any]("services") match {
            case None => >>("services", List(source))
            case Some(list: List[_]) =>
            case Some(breed: String) => >>("services", List(new YamlObject() += ("breed" -> breed)))
            case Some(m) => >>("services", List(m))
          }
          >>("services", <<![List[_]]("services").map { element =>
            if (element.isInstanceOf[String]) {
              new YamlObject() += ("breed" -> (new YamlObject() += ("name" -> element)))
            } else {
              implicit val source = element.asInstanceOf[YamlObject]
              <<?[Any]("breed") match {
                case None => <<?[Any]("name") match {
                  case None =>
                  case Some(_) => >>("breed", source)
                }
                case _ =>
              }
              <<?[Any]("routing") match {
                case None =>
                case Some(s: String) =>
                case Some(s) => expandToList("routing" :: "filters")
              }
              element
            }
          })
        case _ =>
      }
      case _ =>
    }
    super.expand
  }

  override def parse(implicit source: YamlObject): Blueprint = {

    val clusters = <<?[YamlObject]("clusters") match {
      case None => List[Cluster]()
      case Some(map) => map.map({
        case (name: String, cluster: collection.Map[_, _]) =>
          implicit val source = cluster.asInstanceOf[YamlObject]
          val sla = SlaReader.readOptionalReferenceOrAnonymous("sla")

          <<?[List[YamlObject]]("services") match {
            case None => Cluster(name, List(), sla)
            case Some(list) => Cluster(name, list.map(parseService(_)), sla)
          }
      }).toList
    }

    DefaultBlueprint(name, clusters, ports("endpoints"), environmentVariables())
  }

  override protected def validate(bp: Blueprint): Blueprint = bp match {
    case blueprint: BlueprintReference => blueprint
    case blueprint: DefaultBlueprint =>

      validateEndpoints(blueprint)
      validateEnvironmentVariables(blueprint)
      validateRoutingWeights(blueprint)

      val breeds = blueprint.clusters.flatMap(_.services.map(_.breed))
      validateBreeds(breeds)
      validateDependencies(breeds)
      breeds.foreach(BreedReader.validateNonRecursiveDependencies)

      blueprint
  }

  protected def validateEndpoints(blueprint: DefaultBlueprint): Unit = {
//    blueprint.endpoints.find({ port =>
    //      TraitReference.referenceFor(port.name) match {
    //        case (Some(TraitReference(cluster, "ports", name)), _) =>
    //          blueprint.clusters.find(_.name == cluster) match {
    //            case None => true
    //            case Some(c) => c.services.exists(_.breed match {
    //              case _: DefaultBreed => true
    //              case _ => false
    //            }) && c.services.find({
    //              service => service.breed match {
    //                case breed: DefaultBreed => breed.ports.exists(_.name.toString == name)
    //                case _ => false
    //              }
    //            }).isEmpty
    //          }
    //        case _ => true
    //      }
    //    }).flatMap {
    //      case (port, value) => error(UnresolvedEndpointPortError(port, value))
    //    }
  }

  protected def validateEnvironmentVariables(blueprint: DefaultBlueprint): Unit = {
    //    blueprint.environmentVariables.find({
    //      case (Trait.Name(Some(scope), Some(group), name), _) =>
    //        blueprint.clusters.find(_.name == scope) match {
    //          case None => true
    //          case Some(cluster) => cluster.services.exists(_.breed match {
    //            case _: DefaultBreed => true
    //            case _ => false
    //          }) && cluster.services.find({
    //            service => service.breed match {
    //              case breed: DefaultBreed => breed.inTraits.exists(_.name.toString == name)
    //              case _ => false
    //            }
    //          }).isEmpty
    //        }
    //      case _ => true
    //    }).flatMap {
    //      case (name, value) => error(UnresolvedParameterError(name, value))
    //    }
  }

  protected def validateRoutingWeights(blueprint: DefaultBlueprint): Unit = {
    blueprint.clusters.find({ cluster =>
      val weights = cluster.services.map(_.routing).flatten.filter(_.isInstanceOf[DefaultRouting]).map(_.asInstanceOf[DefaultRouting]).map(_.weight).flatten
      weights.exists(_ < 0) || weights.sum > 100
    }).flatMap {
      case cluster => error(RoutingWeightError(cluster))
    }
  }

  protected def validateBreeds(breeds: List[Breed]): Unit = {
    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 => error(NonUniqueBlueprintBreedReferenceError(name))
    }
  }

  protected def validateDependencies(breeds: List[Breed]): Unit = {
    breeds.flatMap({
      case breed: DefaultBreed => breed.dependencies.map((breed, _))
      case _ => List()
    }).find({
      case (breed, dependency) => breeds.find(_.name == dependency._2.name).isEmpty
    }).flatMap {
      case (breed, dependency) => error(UnresolvedBreedDependencyError(breed, dependency))
    }
  }

  private def parseService(implicit source: YamlObject): Service =
    Service(BreedReader.readReference(<<![Any]("breed")), ScaleReader.readOptionalReferenceOrAnonymous("scale"), RoutingReader.readOptionalReferenceOrAnonymous("routing"))
}

object BlueprintReader extends AbstractBlueprintReader {

  override protected def validate(blueprint: Blueprint) = {
    super.validate(blueprint)
    blueprint match {
      case bp: AbstractBlueprint => validateScaleEscalations(bp)
      case _ =>
    }
    blueprint
  }

  def validateScaleEscalations(blueprint: AbstractBlueprint): Unit = {
    blueprint.clusters.foreach { cluster =>
      cluster.sla match {
        case None =>
        case Some(s) => s.escalations.foreach {
          case escalation: ScaleEscalation[_] => escalation.targetCluster match {
            case None =>
            case Some(clusterName) => blueprint.clusters.find(_.name == clusterName) match {
              case None => error(UnresolvedScaleEscalationTargetCluster(cluster, clusterName))
              case Some(_) =>
            }
          }
          case _ =>
        }
      }
    }
  }
}

object DeploymentBlueprintReader extends AbstractBlueprintReader {
  override protected def validateDependencies(breeds: List[Breed]): Unit = {}
}

object ScaleReader extends YamlReader[Scale] with WeakReferenceYamlReader[Scale] {

  override protected def createReference(implicit source: YamlObject): Scale = ScaleReference(reference)

  override protected def createDefault(implicit source: YamlObject): Scale = DefaultScale(name, <<![Double]("cpu"), <<![Double]("memory"), <<![Int]("instances"))
}

object RoutingReader extends YamlReader[Routing] with WeakReferenceYamlReader[Routing] {

  override protected def createReference(implicit source: YamlObject): Routing = RoutingReference(reference)

  override protected def createDefault(implicit source: YamlObject): Routing = DefaultRouting(name, <<?[Int]("weight"), filters)

  protected def filters(implicit source: YamlObject): List[Filter] = <<?[YamlList]("filters") match {
    case None => List[Filter]()
    case Some(list: YamlList) => list.map {
      FilterReader.readReferenceOrAnonymous
    }
  }
}

object FilterReader extends YamlReader[Filter] with WeakReferenceYamlReader[Filter] {

  override protected def createReference(implicit source: YamlObject): Filter = FilterReference(reference)

  override protected def createDefault(implicit source: YamlObject): Filter = DefaultFilter(name, <<![String]("condition"))
}

