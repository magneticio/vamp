package io.vamp.persistence.db

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.model.artifact._
import io.vamp.model.workflow.Workflow

import scala.concurrent.Future
import scala.language.postfixOps

trait PersistenceMultiplexer {
  this: ExecutionContextProvider ⇒

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
    Future.sequence(artifacts.response.map(combine)).map { response ⇒ artifacts.copy(response = response.flatten) }
  }

  protected def combine(artifact: Artifact): Future[Option[Artifact]] = artifact match {
    case gateway: Gateway            ⇒ combine(gateway)
    case deployment: Deployment      ⇒ combine(deployment)
    case blueprint: DefaultBlueprint ⇒ combine(blueprint)
    case workflow: Workflow          ⇒ combine(workflow)
    case _                           ⇒ Future.successful(Option(artifact))
  }

  private def split(blueprint: DefaultBlueprint, each: Artifact ⇒ Future[Artifact]): Future[List[Artifact]] = Future.sequence {
    blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(each) :+ each(blueprint)
  }

  protected def removeGateway(name: String, each: (String, Class[_ <: Artifact]) ⇒ Future[Boolean]): Future[List[Boolean]] = {
    def default = each(name, classOf[GatewayPort]) :: each(name, classOf[GatewayServiceAddress]) :: each(name, classOf[GatewayDeploymentStatus]) :: each(name, classOf[Gateway]) :: Nil

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
      port ← if (!gateway.port.assigned) {
        get(gateway.name, classOf[GatewayPort]).map {
          case Some(gp) ⇒ gateway.port.copy(number = gp.asInstanceOf[GatewayPort].port) match {
            case p ⇒ p.copy(value = Option(p.toValue))
          }
          case _ ⇒ gateway.port
        }
      } else {
        Future.successful(gateway.port)
      }

      service ← get(gateway.name, classOf[GatewayServiceAddress]).map {
        case Some(gp: GatewayServiceAddress) ⇒ Option(GatewayService(gp.host, gateway.port.copy(number = gp.port) match { case p ⇒ p.copy(value = Option(p.toValue)) }))
        case _                               ⇒ None
      }

      routes ← Future.sequence(gateway.routes.map {
        case route: DefaultRoute ⇒ get(route.path.normalized, classOf[RouteTargets]).map {
          case Some(rt: RouteTargets) ⇒ route.copy(targets = rt.targets)
          case _                      ⇒ route.copy(targets = Nil)
        }
        case route ⇒ Future.successful(route)
      })

      deployed ← get(gateway.name, classOf[GatewayDeploymentStatus]).map(_.getOrElse(GatewayDeploymentStatus("", deployed = false)).asInstanceOf[GatewayDeploymentStatus])
    } yield {
      Option(gateway.copy(port = port, service = service, routes = routes, deployed = deployed.deployed))
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
              breed ⇒ service.copy(breed = breed)
            }
          }
        }
        services.map(services ⇒ cluster.copy(services = services))
      }
    }
    clusters.map(clusters ⇒ Option(blueprint.copy(clusters = clusters)))
  }

  private def combine(deployment: Deployment): Future[Option[Deployment]] = {
    import DevelopmentPersistenceOperations._

    for {
      clusters ← Future.sequence {
        deployment.clusters.map { cluster ⇒
          for {
            routing ← Future.sequence {
              cluster.gateways.filter(_.routes.nonEmpty).map { gateway ⇒
                val name = DeploymentCluster.gatewayNameFor(deployment, cluster, gateway.port)
                get(name, classOf[InternalGateway]).flatMap {
                  case Some(InternalGateway(g)) ⇒ combine(g).map(_.getOrElse(gateway))
                  case _                     ⇒ Future.successful(gateway)
                } map { g ⇒
                  g.copy(name = name, port = g.port.copy(name = gateway.port.name))
                }
              }
            }

            services ← Future.sequence {
              cluster.services.map { service ⇒
                for {
                  state ← get(serviceArtifactName(deployment, cluster, service), classOf[DeploymentServiceState]).map {
                    _.getOrElse(DeploymentServiceState("", service.state)).asInstanceOf[DeploymentServiceState].state
                  }
                  scale ← get(serviceArtifactName(deployment, cluster, service), classOf[DeploymentServiceScale]).map {
                    _.orElse(service.scale.map(DeploymentServiceScale("", _))).asInstanceOf[Option[DeploymentServiceScale]].map(_.scale)
                  }
                  instances ← get(serviceArtifactName(deployment, cluster, service), classOf[DeploymentServiceInstances]).map {
                    _.getOrElse(DeploymentServiceInstances("", service.instances)).asInstanceOf[DeploymentServiceInstances].instances
                  }
                  environmentVariables ← get(serviceArtifactName(deployment, cluster, service), classOf[DeploymentServiceEnvironmentVariables]).map {
                    _.getOrElse(DeploymentServiceEnvironmentVariables("", service.environmentVariables)).asInstanceOf[DeploymentServiceEnvironmentVariables].environmentVariables
                  }
                } yield service.copy(state = state, scale = scale, instances = instances, environmentVariables = environmentVariables)
              }
            }
          } yield {
            cluster.copy(services = services.filterNot(_.state.isUndeployed), gateways = routing)
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
            Port(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.Ports), port.name).toString, None, cluster.portBy(port.name).flatMap(n ⇒ Some(n.toString)))
          })
        } map { p ⇒ p.name -> p } toMap

        val environmentVariables = (deployment.environmentVariables ++ clusters.flatMap { cluster ⇒
          cluster.services.flatMap(_.environmentVariables).map(ev ⇒ ev.copy(name = TraitReference(cluster.name, TraitReference.groupFor(TraitReference.EnvironmentVariables), ev.name).toString))
        }) map { p ⇒ p.name -> p } toMap

        Option(deployment.copy(gateways = Nil, clusters = clusters, hosts = hosts, ports = ports.values.toList, environmentVariables = environmentVariables.values.toList))

      } else None
    }
  }

  private def combine(workflow: Workflow): Future[Option[Workflow]] = {
    for {
      scale ← get(workflow.name, classOf[WorkflowScale]).asInstanceOf[Future[Option[WorkflowScale]]].map {
        _.map(_.scale).orElse(workflow.scale)
      }
      network ← get(workflow.name, classOf[WorkflowNetwork]).asInstanceOf[Future[Option[WorkflowNetwork]]].map {
        _.map(_.network).orElse(workflow.network)
      }
      arguments ← get(workflow.name, classOf[WorkflowArguments]).asInstanceOf[Future[Option[WorkflowArguments]]].map {
        _.map(_.arguments).getOrElse(workflow.arguments)
      }
    } yield Option(workflow.copy(scale = scale, network = network, arguments = arguments))
  }

  private def get[A <: Artifact](artifact: A): Future[Option[A]] = get(artifact.name, artifact.getClass).asInstanceOf[Future[Option[A]]]

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]
}

