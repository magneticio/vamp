package io.vamp.persistence

import akka.actor.Actor
import io.vamp.common.notification.NotificationProvider
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

trait DeploymentPersistenceOperations extends PersistenceApi {
  this: NotificationProvider with PatchPersistenceOperations ⇒

  import PersistenceActor._

  def receive: Actor.Receive = {

    case o: UpdateDeploymentServiceStatus               ⇒ patch(o.deployment, o.cluster, o.service, o.status)

    case o: UpdateDeploymentServiceScale                ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(scale = Option(o.scale)), (d, m) ⇒ replyUpdate(d, s"deployment-service-scales:${o.deployment.name}", o.source, m))

    case o: UpdateDeploymentServiceInstances            ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(instances = o.instances))

    case o: UpdateDeploymentServiceEnvironmentVariables ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(environmentVariables = o.environmentVariables))

    case o: UpdateDeploymentServiceHealth               ⇒ patch(o.deployment, o.cluster, o.service, s ⇒ s.copy(health = Option(o.health)))

    case o: ResetDeploymentService                      ⇒ reset(o.deployment, o.cluster, o.service)
  }

  private def patch(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, status: DeploymentService.Status): Unit = {
    var modified = false
    get(deployment).map { d ⇒
      d.copy(clusters = d.clusters.map {
        case c if c.name == cluster.name ⇒ c.copy(services = c.services.map {
          case s if s.breed.name == service.breed.name ⇒
            val ns = s.copy(status = status)
            modified = ns != s
            ns
          case s ⇒ s
        }.filterNot(_.status.isUndeployed))
        case c ⇒ c
      }.filterNot(_.services.isEmpty))
    } foreach { d ⇒
      if (modified) {
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
            d.copy(hosts = hosts, ports = ports.values.toList, environmentVariables = environmentVariables.values.toList),
            update = true
          )
        }
        else replyDelete(d, remove = true)
      }
      else replyUpdate(d, update = false)
    }
  }

  private def reset(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    patch(deployment, cluster, service, s ⇒ {
      s.copy(instances = Nil, health = None, environmentVariables = s.environmentVariables.map(_.copy(interpolated = None)))
    })
  }

  private def patch(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, using: DeploymentService ⇒ DeploymentService): Unit = {
    patch(deployment, cluster, service, using, (d, m) ⇒ replyUpdate(d, m))
  }

  private def patch(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, using: DeploymentService ⇒ DeploymentService, update: (Deployment, Boolean) ⇒ Unit): Unit = {
    var modified = false
    get(deployment).map { d ⇒
      d.copy(clusters = d.clusters.map {
        case c if c.name == cluster.name ⇒ c.copy(services = c.services.map {
          case s if s.breed.name == service.breed.name ⇒
            val ns = using(s)
            modified = ns != s
            ns
          case s ⇒ s
        })
        case c ⇒ c
      })
    } foreach { d ⇒
      update(d, modified)
    }
  }
}
