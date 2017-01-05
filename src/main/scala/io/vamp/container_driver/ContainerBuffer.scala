package io.vamp.container_driver

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ Actor, ActorRef }
import io.vamp.container_driver.ContainerDriverActor.{ Deploy, DeploymentServices, Get, Undeploy }
import io.vamp.model.artifact.{ Deployment, DeploymentCluster, DeploymentService }

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

private[container_driver] case class ContainerBufferEntry(
  touched:          OffsetDateTime,
  reconciled:       Option[OffsetDateTime],
  containerService: ContainerService
)

private[container_driver] case class ContainerChangeEvent(id: String)

private[container_driver] case class ReconcileRequest(deployment: Deployment, service: DeploymentService)

trait ContainerBuffer {
  this: ContainerDriverActor with ContainerDriver ⇒

  private var store: mutable.Map[ActorRef, Map[String, ContainerBufferEntry]] = new mutable.HashMap()

  protected def expirationPeriod: Duration

  protected def reconciliationPeriod: Duration

  protected def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any]

  protected def undeploy(deployment: Deployment, service: DeploymentService): Future[Any]

  def receive: Actor.Receive = {
    case Get(services)            ⇒ processGet(services)
    case d: Deploy                ⇒ reply(processDeploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy              ⇒ reply(processUndeploy(u.deployment, u.service))

    case ContainerChangeEvent(id) ⇒ changed(id)
    case cs: ContainerService     ⇒ update(cs)
  }

  private def processGet(deploymentServices: List[DeploymentServices]) = {
    cleanup()
    (entries andThen update andThen reconcile andThen respond)(deploymentServices)
  }

  private def processDeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    invalidate(deployment, service)
    deploy(deployment, cluster, service, update)
  }

  private def processUndeploy(deployment: Deployment, service: DeploymentService) = {
    invalidate(deployment, service)
    undeploy(deployment, service)
  }

  private def entries: List[DeploymentServices] ⇒ Map[String, ContainerBufferEntry] = { deploymentServices ⇒
    val now = OffsetDateTime.now()
    deploymentServices.flatMap { ds ⇒
      ds.services.map { service ⇒
        appId(ds.deployment, service.breed) → ContainerBufferEntry(now, None, ContainerService(ds.deployment, service, None))
      }
    }.toMap
  }

  private def update: Map[String, ContainerBufferEntry] ⇒ Map[String, ContainerBufferEntry] = { entries ⇒
    val watcher = sender()

    store.get(watcher) match {
      case Some(existing) ⇒

        val newEntries = entries.map {
          case (k, v) ⇒
            val value = existing.get(k) match {
              case Some(old) ⇒ v.copy(reconciled = old.reconciled, containerService = old.containerService)
              case None      ⇒ v
            }
            k → value
        }

        store.put(watcher, existing ++ newEntries)
        newEntries

      case None ⇒
        store.put(watcher, entries)
        entries
    }
  }

  private def cleanup() = {
    val boundary = OffsetDateTime.now().minus(expirationPeriod.toSeconds, ChronoUnit.SECONDS)
    store = store.map {
      case (watcher, entries) ⇒ watcher → entries.filter { case (_, value) ⇒ boundary.isBefore(value.touched) }
    }.filter(_._2.nonEmpty)
  }

  private def reconcile: Map[String, ContainerBufferEntry] ⇒ Map[String, ContainerBufferEntry] = { entries ⇒
    val boundary = OffsetDateTime.now().minus(reconciliationPeriod.toSeconds, ChronoUnit.SECONDS)

    val (request, respond) = entries.partition {
      case (_, entry) ⇒
        entry.reconciled.isEmpty || boundary.isAfter(entry.reconciled.get)
    }

    request.values.foreach { entry ⇒
      self ! ReconcileRequest(entry.containerService.deployment, entry.containerService.service)
    }

    respond
  }

  private def respond: Map[String, ContainerBufferEntry] ⇒ Unit = { entries ⇒
    val watcher = sender()
    entries.foreach {
      case (_, entry) ⇒ watcher ! entry.containerService
    }
  }

  private def changed(id: String) = {
    store.values.flatMap(_.values).map(_.containerService).find { cs ⇒
      appId(cs.deployment, cs.service.breed) == id
    }.foreach { cs ⇒
      self ! ReconcileRequest(cs.deployment, cs.service)
    }
  }

  private def update(containerService: ContainerService) = {
    val now = OffsetDateTime.now()
    val id = appId(containerService.deployment, containerService.service.breed)
    store = store.map {
      case (watcher, entries) ⇒
        watcher → entries.map {
          case (key, value) if key == id ⇒
            val cs = containerService.copy(value.containerService.deployment, value.containerService.service)
            watcher ! cs
            key → value.copy(containerService = cs, reconciled = Option(now))
          case (key, value) ⇒ key → value
        }
    }
  }

  private def invalidate(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    store = store.map {
      case (watcher, entries) ⇒
        watcher → entries.map {
          case (key, value) if key == id ⇒ key → value.copy(reconciled = None)
          case (key, value)              ⇒ key → value
        }
    }
  }
}
