package io.vamp.persistence

import akka.actor.Actor
import io.vamp.model.artifact._

import scala.language.postfixOps

trait DeploymentPersistenceMessages {

  case class UpdateDeploymentServiceStatus(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, status: DeploymentService.Status) extends PersistenceActor.PersistenceMessages

  case class UpdateDeploymentServiceScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) extends PersistenceActor.PersistenceMessages

  case class UpdateDeploymentServiceInstances(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, instances: List[Instance]) extends PersistenceActor.PersistenceMessages

  case class UpdateDeploymentServiceEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, environmentVariables: List[EnvironmentVariable]) extends PersistenceActor.PersistenceMessages

  case class UpdateDeploymentServiceHealth(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, health: Health) extends PersistenceActor.PersistenceMessages

  case class ResetDeploymentService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends PersistenceActor.PersistenceMessages

}

private[persistence] object DeploymentPersistenceOperations {

  def clusterArtifactName(deployment: Deployment, cluster: DeploymentCluster): String = {
    GatewayPath(deployment.name :: cluster.name :: Nil).normalized
  }

  def serviceArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): String = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: Nil).normalized
  }

  def servicePortArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, port: Port): String = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: port.name :: Nil).normalized
  }
}

trait DeploymentPersistenceOperations {
  this: PersistenceApi with PatchPersistenceOperations ⇒

  import PersistenceActor._

  def receive: Actor.Receive = {

    case o: UpdateDeploymentServiceStatus               ⇒ patch(o.deployment, o.cluster, o.service, o.status)

    case o: UpdateDeploymentServiceScale                ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(scale = Option(o.scale)), d ⇒ replyUpdate(d, "deployment-service-scales", o.source))

    case o: UpdateDeploymentServiceInstances            ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(instances = o.instances))

    case o: UpdateDeploymentServiceEnvironmentVariables ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(environmentVariables = o.environmentVariables))

    case o: UpdateDeploymentServiceHealth               ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(health = Option(o.health)))

    case o: ResetDeploymentService                      ⇒ reset(o.deployment, o.cluster, o.service)
  }

  private def patch(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, status: DeploymentService.Status): Unit = {
    get(deployment).map { d ⇒
      d.copy(clusters = d.clusters.map {
        case c if c.name == cluster.name ⇒ c.copy(services = c.services.map {
          case s if s.breed.name == service.breed.name ⇒ s.copy(status = status)
          case s                                       ⇒ s
        }.filterNot(_.status.isUndeployed))
        case c ⇒ c
      }.filterNot(_.services.isEmpty))
    } foreach { d ⇒
      if (d.clusters.nonEmpty) {
        val hosts = d.hosts.filter { host ⇒
          TraitReference.referenceFor(host.name).flatMap(ref ⇒ d.clusters.find(_.name == ref.cluster)).isDefined
        }
        val ports = d.clusters.flatMap { cluster ⇒
          cluster.services.map(_.breed).flatMap(_.ports).map({ port ⇒
            Port(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.Ports), port.name).toString, None, cluster.portBy(port.name).flatMap(n ⇒ Some(n.toString)))
          })
        } map { p ⇒ p.name → p } toMap
        val environmentVariables = (d.environmentVariables ++ d.clusters.flatMap { cluster ⇒
          cluster.services.flatMap(_.environmentVariables).map(ev ⇒ ev.copy(name = TraitReference(cluster.name, TraitReference.groupFor(TraitReference.EnvironmentVariables), ev.name).toString))
        }) map { p ⇒ p.name → p } toMap

        replyUpdate(
          d.copy(gateways = Nil, hosts = hosts, ports = ports.values.toList, environmentVariables = environmentVariables.values.toList)
        )
      }
      else replyDelete(d)
    }
  }

  private def reset(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    patch(deployment, cluster, service, s ⇒ {
      s.copy(instances = Nil, health = None, environmentVariables = s.environmentVariables.map(_.copy(interpolated = None)))
    })
  }

  private def patch(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, using: DeploymentService ⇒ DeploymentService): Unit = {
    patch(deployment, cluster, service, using, d ⇒ replyUpdate(d))
  }

  private def patch(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, using: DeploymentService ⇒ DeploymentService, store: Deployment ⇒ Unit): Unit = {
    get(deployment).map { d ⇒
      d.copy(clusters = d.clusters.map {
        case c if c.name == cluster.name ⇒ c.copy(services = c.services.map {
          case s if s.breed.name == service.breed.name ⇒ using(s)
          case s                                       ⇒ s
        })
        case c ⇒ c
      })
    } foreach {
      store
    }
  }
}
