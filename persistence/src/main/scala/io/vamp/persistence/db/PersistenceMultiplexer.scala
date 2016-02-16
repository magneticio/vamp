package io.vamp.persistence.db

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.model.artifact._
import io.vamp.persistence.operation._

import scala.concurrent.Future
import scala.language.postfixOps

trait PersistenceMultiplexer {
  this: ExecutionContextProvider ⇒

  import DeploymentPersistence._

  protected def split(artifact: Artifact, each: Artifact ⇒ Future[Artifact]): Future[List[Artifact]] = artifact match {
    case blueprint: DefaultBlueprint ⇒ split(blueprint, each)
    case _                           ⇒ Future.sequence(each(artifact) :: Nil)
  }

  protected def remove(name: String, `type`: Class[_ <: Artifact], each: (String, Class[_ <: Artifact]) ⇒ Future[Boolean]): Future[List[Boolean]] = `type` match {
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ removeGateway(name, each)
    case _                                         ⇒ Future.sequence(each(name, `type`) :: Nil)
  }

  protected def combine(artifact: Option[Artifact]): Future[Option[Artifact]] = {
    if (artifact.isDefined) combine(artifact.get) else Future.successful(None)
  }

  protected def combine(artifacts: ArtifactResponseEnvelope): Future[ArtifactResponseEnvelope] = {
    Future.sequence(artifacts.response.map(combine)).map { case response ⇒ artifacts.copy(response = response.flatten) }
  }

  protected def combine(artifact: Artifact): Future[Option[Artifact]] = artifact match {
    case gateway: Gateway            ⇒ combine(gateway)
    case deployment: Deployment      ⇒ combine(deployment)
    case blueprint: DefaultBlueprint ⇒ combine(blueprint)
    case _                           ⇒ Future.successful(Option(artifact))
  }

  private def split(blueprint: DefaultBlueprint, each: Artifact ⇒ Future[Artifact]): Future[List[Artifact]] = Future.sequence {
    blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(each) :+ each(blueprint)
  }

  protected def removeGateway(name: String, each: (String, Class[_ <: Artifact]) ⇒ Future[Boolean]): Future[List[Boolean]] = {
    def default = each(name, classOf[GatewayPort]) :: each(name, classOf[GatewayDeploymentStatus]) :: each(name, classOf[Gateway]) :: Nil

    get(name, classOf[Gateway]).flatMap {
      case Some(gateway: Gateway) ⇒ Future.sequence {
        gateway.routes.map(route ⇒ each(route.path.normalized, classOf[DefaultRoute])) ++ default
      }
      case _ ⇒ Future.sequence {
        default
      }
    }
  }

  private def combine(gateway: Gateway): Future[Option[Gateway]] = {
    for {
      port ← if (gateway.port.value.isEmpty)
        get(gateway.name, classOf[GatewayPort]).map(gp ⇒ gateway.port.copy(value = gp.map(_.asInstanceOf[GatewayPort].port)))
      else
        Future.successful(gateway.port)

      routes ← Future.sequence(gateway.routes.map {
        case route: DefaultRoute ⇒ get(route.path.normalized, classOf[RouteTargets]).map {
          case Some(rt: RouteTargets) ⇒ route.copy(targets = rt.targets)
          case _                      ⇒ route.copy(targets = Nil)
        }
        case route ⇒ Future.successful(route)
      })

      deployed ← get(gateway.name, classOf[GatewayDeploymentStatus]).map(_.getOrElse(GatewayDeploymentStatus("", deployed = false)).asInstanceOf[GatewayDeploymentStatus])
    } yield {
      Option(gateway.copy(port = port, routes = routes, deployed = deployed.deployed))
    }
  }

  private def combine(blueprint: DefaultBlueprint): Future[Option[DefaultBlueprint]] = {
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
    clusters.map(clusters ⇒ Option(blueprint.copy(clusters = clusters)))
  }

  private def combine(deployment: Deployment): Future[Option[Deployment]] = {
    for {
      gateways ← Future.sequence {
        deployment.gateways.map(gateway ⇒ get(gateway.copy(name = Deployment.gatewayNameFor(deployment, gateway))))
      }

      clusters ← Future.sequence {
        deployment.clusters.map { cluster ⇒
          for {
            routing ← Future.sequence {
              cluster.routing.map { gateway ⇒
                gateway.copy(name = DeploymentCluster.gatewayNameFor(deployment, cluster, gateway.port))
              } map combine
            } map (_.flatten)
            services ← Future.sequence {
              cluster.services.map { service ⇒
                for {
                  state ← get(serviceArtifactName(deployment, cluster, service), classOf[DeploymentServiceState]).map {
                    _.getOrElse(DeploymentServiceState("", service.state)).asInstanceOf[DeploymentServiceState].state
                  }
                  instances ← get(serviceArtifactName(deployment, cluster, service), classOf[DeploymentServiceInstances]).map {
                    _.getOrElse(DeploymentServiceInstances("", service.instances)).asInstanceOf[DeploymentServiceInstances].instances
                  }
                  environmentVariables ← get(serviceArtifactName(deployment, cluster, service), classOf[DeploymentServiceEnvironmentVariables]).map {
                    _.getOrElse(DeploymentServiceEnvironmentVariables("", service.environmentVariables)).asInstanceOf[DeploymentServiceEnvironmentVariables].environmentVariables
                  }
                } yield service.copy(state = state, instances = instances, environmentVariables = environmentVariables)
              }
            }
          } yield {

            val portMapping = cluster.portMapping.map {
              case (name, value) ⇒ name -> routing.find(gateway ⇒ GatewayPath(gateway.name).segments.last == name).map(_.port.number).getOrElse(value)
            }

            cluster.copy(services = services.filterNot(_.state.isUndeployed), routing = routing, portMapping = portMapping)
          }
        }
      } map {
        _.flatMap { cluster ⇒
          val services = cluster.services.filterNot(_.state.isUndeployed)
          if (services.isEmpty) Nil else cluster.copy(services = services) :: Nil
        }
      }

    } yield {

      if (clusters.nonEmpty) {
        val hosts = deployment.hosts.filter { host ⇒
          TraitReference.referenceFor(host.name).flatMap(ref ⇒ clusters.find(_.name == ref.cluster)).isDefined
        }

        val ports = clusters.flatMap { cluster ⇒
          cluster.services.map(_.breed).flatMap(_.ports).map({ port ⇒
            Port(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.Ports), port.name).toString, None, cluster.portMapping.get(port.name).flatMap(n ⇒ Some(n.toString)))
          })
        } map { p ⇒ p.name -> p } toMap

        val environmentVariables = clusters.flatMap { cluster ⇒
          cluster.services.flatMap(_.environmentVariables).map(ev ⇒ ev.copy(name = TraitReference(cluster.name, TraitReference.groupFor(TraitReference.EnvironmentVariables), ev.name).toString))
        } map { p ⇒ p.name -> p } toMap

        Option(deployment.copy(gateways = gateways.flatten, clusters = clusters, hosts = hosts, ports = ports.values.toList, environmentVariables = environmentVariables.values.toList))

      } else None
    }
  }

  private def get[A <: Artifact](artifact: A): Future[Option[A]] = get(artifact.name, artifact.getClass).asInstanceOf[Future[Option[A]]]

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]
}

