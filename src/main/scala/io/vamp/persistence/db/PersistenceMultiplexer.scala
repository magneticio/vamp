package io.vamp.persistence.db

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.model.artifact._
import io.vamp.persistence.operation.{ GatewayDeploymentStatus, GatewayPort, RouteTargets }

import scala.concurrent.Future
import scala.language.postfixOps

trait PersistenceMultiplexer {
  this: ExecutionContextProvider ⇒

  protected def split(artifact: Artifact, each: Artifact ⇒ Future[Artifact]): Future[List[Artifact]] = artifact match {
    case deployment: Deployment      ⇒ split(deployment, each)
    case blueprint: DefaultBlueprint ⇒ split(blueprint, each)
    case _                           ⇒ Future.sequence(each(artifact) :: Nil)
  }

  protected def remove(name: String, `type`: Class[_ <: Artifact], each: (String, Class[_ <: Artifact]) ⇒ Future[Option[Artifact]]): Future[List[Option[Artifact]]] = `type` match {
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ removeGateway(name, each)
    case _                                         ⇒ Future.sequence(each(name, `type`) :: Nil)
  }

  protected def combine(artifact: Option[Artifact]): Future[Option[Artifact]] = {
    if (artifact.isDefined) combine(artifact.get).map(Option(_)) else Future.successful(None)
  }

  protected def combine(artifacts: ArtifactResponseEnvelope): Future[ArtifactResponseEnvelope] = {
    Future.sequence(artifacts.response.map(combine)).map { case response ⇒ artifacts.copy(response = response) }
  }

  protected def combine(artifact: Artifact): Future[Artifact] = artifact match {
    case gateway: Gateway            ⇒ combine(gateway)
    case deployment: Deployment      ⇒ combine(deployment)
    case blueprint: DefaultBlueprint ⇒ combine(blueprint)
    case _                           ⇒ Future.successful(artifact)
  }

  private def split(blueprint: DefaultBlueprint, each: Artifact ⇒ Future[Artifact]): Future[List[Artifact]] = Future.sequence {
    blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(each) :+ each(blueprint)
  }

  private def split(deployment: Deployment, each: Artifact ⇒ Future[Artifact]): Future[List[Artifact]] = Future.sequence {
    deployment.gateways.map(each) ++ deployment.clusters.flatMap(_.routing).map(each) :+ each(deployment)
  }

  protected def removeGateway(name: String, each: (String, Class[_ <: Artifact]) ⇒ Future[Option[Artifact]]): Future[List[Option[Artifact]]] = {
    def default = each(name, classOf[GatewayPort]) :: each(name, classOf[GatewayDeploymentStatus]) :: each(name, classOf[Gateway]) :: Nil

    get(name, classOf[Gateway]).flatMap {
      case Some(gateway: Gateway) ⇒ Future.sequence {
        gateway.routes.map(route ⇒ each(route.path.normalized, classOf[DeployedRoute])) ++ default
      }
      case _ ⇒ Future.sequence {
        default
      }
    }
  }

  private def combine(gateway: Gateway): Future[Gateway] = {
    for {
      port ← if (gateway.port.value.isEmpty)
        get(gateway.name, classOf[GatewayPort]).map(gp ⇒ gateway.port.copy(value = gp.map(_.asInstanceOf[GatewayPort].port)))
      else
        Future.successful(gateway.port)

      routes ← Future.sequence(gateway.routes.map {
        case route: AbstractRoute ⇒ get(route.path.normalized, classOf[RouteTargets]).map {
          case Some(rt: RouteTargets) ⇒ DeployedRoute(route, rt.targets)
          case _                      ⇒ DeployedRoute(route, Nil)
        }
        case route ⇒ Future.successful(route)
      })

      deployed ← get(gateway.name, classOf[GatewayDeploymentStatus]).map(_.getOrElse(GatewayDeploymentStatus("", deployed = false)).asInstanceOf[GatewayDeploymentStatus])
    } yield {
      gateway.copy(port = port, routes = routes, deployed = deployed.deployed)
    }
  }

  private def combine(blueprint: DefaultBlueprint): Future[DefaultBlueprint] = {
    val clusters = Future.sequence {
      blueprint.clusters.map { cluster ⇒
        val services = Future.sequence {
          cluster.services.map { service ⇒
            (service.breed match {
              case b: DefaultBreed ⇒ get(b).map { result ⇒ result.getOrElse(BreedReference(b.name)) }
              case b               ⇒ Future.successful(b)
            }).map {
              case breed ⇒ service.copy(breed = breed)
            }
          }
        }
        services.map(services ⇒ cluster.copy(services = services))
      }
    }
    clusters.map(clusters ⇒ blueprint.copy(clusters = clusters))
  }

  private def combine(deployment: Deployment): Future[Deployment] = {
    for {
      gateways ← Future.sequence {
        deployment.gateways.map(gateway ⇒ gateway.copy(name = Deployment.gatewayNameFor(deployment, gateway))).map { gateway ⇒ get(gateway) }
      }

      clusters ← Future.sequence {
        deployment.clusters.map { cluster ⇒
          val routing = Future.sequence {
            cluster.routing.map { gateway ⇒
              gateway.copy(name = DeploymentCluster.gatewayNameFor(deployment, cluster, gateway.port))
            } map combine
          }
          routing.map { routing ⇒
            val portMapping = cluster.portMapping.map {
              case (name, value) ⇒ name -> routing.find(gateway ⇒ GatewayPath(gateway.name).segments.last == name).map(_.port.number).getOrElse(value)
            }
            cluster.copy(routing = routing, portMapping = portMapping)
          }
        }
      }
    } yield {

      val ports = clusters.flatMap { cluster ⇒
        cluster.services.map(_.breed).flatMap(_.ports).map({ port ⇒
          Port(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.Ports), port.name).toString, None, cluster.portMapping.get(port.name).flatMap(n ⇒ Some(n.toString)))
        })
      } map { p ⇒ p.name -> p } toMap

      deployment.copy(gateways = gateways.flatten, clusters = clusters, ports = ports.values.toList)
    }
  }

  private def get[A <: Artifact](artifact: A): Future[Option[A]] = get(artifact.name, artifact.getClass).asInstanceOf[Future[Option[A]]]

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]
}

