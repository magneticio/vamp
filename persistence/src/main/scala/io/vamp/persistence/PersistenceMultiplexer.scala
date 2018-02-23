package io.vamp.persistence

import io.vamp.common.Artifact
import io.vamp.model.artifact._

trait PersistenceMultiplexer {

  protected def get[A <: Artifact](artifact: A): Option[A]

  protected def get[T <: Artifact](name: String, `type`: Class[T]): Option[T]

  protected def combine(artifact: Option[Artifact]): Option[Artifact] = {
    if (artifact.isDefined) combineArtifact(artifact.get) else None
  }

  protected def combine(artifacts: ArtifactResponseEnvelope): ArtifactResponseEnvelope = {
    artifacts.copy(response = artifacts.response.flatMap(combineArtifact))
  }

  protected def split(artifact: Artifact, each: Artifact ⇒ Artifact): List[Artifact] = artifact match {
    case blueprint: DefaultBlueprint ⇒ blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(each) :+ each(blueprint)
    case _                           ⇒ each(artifact) :: Nil
  }

  protected def remove(name: String, `type`: Class[_ <: Artifact], each: (String, Class[_ <: Artifact]) ⇒ Boolean): List[Boolean] = `type` match {
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ removeGateway(name, each)
    case _                                         ⇒ each(name, `type`) :: Nil
  }

  private def combineArtifact(artifact: Artifact): Option[Artifact] = artifact match {
    case gateway: Gateway            ⇒ combine(gateway)
    case blueprint: DefaultBlueprint ⇒ combine(blueprint)
    case workflow: Workflow          ⇒ combine(workflow)
    case _                           ⇒ Option(artifact)
  }

  private def removeGateway(name: String, each: (String, Class[_ <: Artifact]) ⇒ Boolean): List[Boolean] = {
    def default = each(name, classOf[GatewayPort]) :: each(name, classOf[GatewayServiceAddress]) :: each(name, classOf[GatewayDeploymentStatus]) :: each(name, classOf[Gateway]) :: Nil

    get(name, classOf[Gateway]) match {
      case Some(gateway: Gateway) ⇒ gateway.routes.map(route ⇒ each(route.path.normalized, classOf[DefaultRoute])) ++ default
      case _                      ⇒ default
    }
  }

  private def combine(gateway: Gateway): Option[Gateway] = {
    val port = {
      if (!gateway.port.assigned) {
        get(gateway.name, classOf[GatewayPort]) match {
          case Some(gp) ⇒
            gateway.port.copy(number = gp.asInstanceOf[GatewayPort].port) match {
              case p ⇒ p.copy(value = Option(p.toValue))
            }
          case _ ⇒ gateway.port
        }
      }
      else gateway.port
    }

    val service = {
      get(gateway.name, classOf[GatewayServiceAddress]) match {
        case Some(gp: GatewayServiceAddress) ⇒ Option(GatewayService(gp.host, gateway.port.copy(number = gp.port) match { case p ⇒ p.copy(value = Option(p.toValue)) }))
        case _                               ⇒ None
      }
    }

    val routes = {
      gateway.routes.map {
        case route: DefaultRoute ⇒ get(route.path.normalized, classOf[RouteTargets]) match {
          case Some(rt: RouteTargets) ⇒ route.copy(targets = rt.targets)
          case _                      ⇒ route.copy(targets = Nil)
        }
        case route ⇒ route
      }
    }

    val deployed = get(gateway.name, classOf[GatewayDeploymentStatus]).getOrElse(GatewayDeploymentStatus("", deployed = false))

    Option(gateway.copy(port = port, service = service, routes = routes, deployed = deployed.deployed))
  }

  private def combine(blueprint: DefaultBlueprint): Option[DefaultBlueprint] = Option(
    blueprint.copy(
      clusters = blueprint.clusters.map { cluster ⇒
        val services = cluster.services.map { service ⇒
          val breed = service.breed match {
            case b: DefaultBreed ⇒ get(b).getOrElse(BreedReference(b.name))
            case b               ⇒ b
          }
          service.copy(breed = breed)
        }
        cluster.copy(services = services)
      }
    )
  )

  private def combine(workflow: Workflow): Option[Workflow] = Option(
    workflow.copy(
      breed = get(workflow.name, classOf[WorkflowBreed]).map(_.breed).getOrElse(workflow.breed),
      status = get(workflow.name, classOf[WorkflowStatus]).map(_.unmarshall).getOrElse(workflow.status),
      scale = get(workflow.name, classOf[WorkflowScale]).map(_.scale).orElse(workflow.scale),
      network = get(workflow.name, classOf[WorkflowNetwork]).map(_.network).orElse(workflow.network),
      arguments = get(workflow.name, classOf[WorkflowArguments]).map(_.arguments).getOrElse(workflow.arguments),
      environmentVariables = get(workflow.name, classOf[WorkflowEnvironmentVariables]).map(_.environmentVariables).getOrElse(workflow.environmentVariables),
      instances = get(workflow.name, classOf[WorkflowInstances]).map(_.instances).getOrElse(Nil),
      health = get(workflow.name, classOf[WorkflowHealth]).flatMap(_.health)
    )
  )
}
