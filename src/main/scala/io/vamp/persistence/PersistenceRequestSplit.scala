package io.vamp.persistence

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.model.artifact._

import scala.concurrent.Future
import scala.language.postfixOps

trait PersistenceRequestSplit {
  this: ExecutionContextProvider ⇒

  protected def split(artifact: Artifact, each: Artifact ⇒ Future[Artifact]): Future[List[Artifact]] = artifact match {
    case deployment: Deployment      ⇒ split(deployment, each)
    case blueprint: DefaultBlueprint ⇒ split(blueprint, each)
    case _                           ⇒ Future.sequence(each(artifact) :: Nil)
  }

  protected def combine(artifact: Option[Artifact]): Future[Option[Artifact]] =
    if (artifact.isDefined) combine(artifact.get).map(Option(_)) else Future.successful(None)

  protected def combine(artifacts: ArtifactResponseEnvelope): Future[ArtifactResponseEnvelope] =
    Future.sequence(artifacts.response.map(combine)).map { case response ⇒ artifacts.copy(response = response) }

  protected def combine(artifact: Artifact): Future[Artifact] = artifact match {
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

  private def combine(deployment: Deployment): Future[Deployment] = for {
    gateways ← Future.sequence {
      deployment.gateways.map(gateway ⇒ gateway.copy(name = Deployment.gatewayNameFor(deployment, gateway))).map { gateway ⇒ get(gateway) }
    }

    clusters ← Future.sequence {
      deployment.clusters.map { cluster ⇒
        val routing = Future.sequence {
          cluster.routing.map { gateway ⇒
            gateway.copy(name = DeploymentCluster.gatewayNameFor(deployment, cluster, gateway.port))
          } map {
            get(_) map { result ⇒
              result.flatMap(gateway ⇒ Option(gateway.copy(port = gateway.port.copy(name = GatewayPath(gateway.name).segments.last))))
            }
          }
        }
        routing.map(routing ⇒
          cluster.copy(routing = routing.flatten))
      }
    }
  } yield deployment.copy(gateways = gateways.flatten, clusters = clusters)

  private def get[A <: Artifact](artifact: A): Future[Option[A]] = get(artifact.name, artifact.getClass).asInstanceOf[Future[Option[A]]]

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]
}
