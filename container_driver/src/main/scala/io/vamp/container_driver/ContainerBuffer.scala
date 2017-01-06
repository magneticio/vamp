package io.vamp.container_driver

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ Actor, ActorRef }
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.model.artifact.{ Deployment, DeploymentCluster, DeploymentService, Workflow }

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

private[container_driver] case class ContainerBufferEntry(
  touched:    OffsetDateTime,
  reconciled: Option[OffsetDateTime],
  runtime:    ContainerRuntime
)

private[container_driver] case class ContainerChangeEvent(id: String)

private[container_driver] case class ReconcileWorkflow(workflow: Workflow)

private[container_driver] case class ReconcileService(deployment: Deployment, service: DeploymentService)

trait ContainerBuffer {
  this: ContainerDriverActor with ContainerDriver ⇒

  private var store: mutable.Map[ActorRef, Map[String, ContainerBufferEntry]] = new mutable.HashMap()

  protected def expirationPeriod: Duration

  protected def reconciliationPeriod: Duration

  protected def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any]

  protected def undeploy(deployment: Deployment, service: DeploymentService): Future[Any]

  protected def deploy(workflow: Workflow, update: Boolean): Future[Any]

  protected def undeploy(workflow: Workflow): Future[Any]

  def receive: Actor.Receive = {
    case Get(services)            ⇒ processGet(services)
    case d: Deploy                ⇒ reply(processDeploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy              ⇒ reply(processUndeploy(u.deployment, u.service))

    case GetWorkflow(workflow)    ⇒ processGet(workflow)
    case d: DeployWorkflow        ⇒ reply(processDeploy(d.workflow, d.update))
    case u: UndeployWorkflow      ⇒ reply(processUndeploy(u.workflow))

    case ContainerChangeEvent(id) ⇒ changed(id)

    case cs: ContainerService     ⇒ update(cs)
    case cw: ContainerWorkflow    ⇒ update(cw)
  }

  private def processGet(any: AnyRef) = {
    cleanup()
    (entries andThen update andThen reconcile andThen respond)(any)
  }

  private def processDeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    invalidate(appId(deployment, service.breed))
    deploy(deployment, cluster, service, update)
  }

  private def processUndeploy(deployment: Deployment, service: DeploymentService) = {
    invalidate(appId(deployment, service.breed))
    undeploy(deployment, service)
  }

  private def processDeploy(workflow: Workflow, update: Boolean) = {
    invalidate(appId(workflow))
    deploy(workflow, update)
  }

  private def processUndeploy(workflow: Workflow) = {
    invalidate(appId(workflow))
    undeploy(workflow)
  }

  private def entries: AnyRef ⇒ Map[String, ContainerBufferEntry] = {
    case deploymentServices: List[_] ⇒
      val now = OffsetDateTime.now()
      deploymentServices.filter(_.isInstanceOf[DeploymentServices]).map(_.asInstanceOf[DeploymentServices]).flatMap { ds ⇒
        ds.services.map { service ⇒
          appId(ds.deployment, service.breed) → ContainerBufferEntry(now, None, ContainerService(ds.deployment, service, None))
        }
      }.toMap
    case workflow: Workflow ⇒ Map(appId(workflow) → ContainerBufferEntry(OffsetDateTime.now(), None, ContainerWorkflow(workflow, None)))
    case _                  ⇒ Map()
  }

  private def update: Map[String, ContainerBufferEntry] ⇒ Map[String, ContainerBufferEntry] = { entries ⇒
    val watcher = sender()
    store.get(watcher) match {
      case Some(existing) ⇒

        val newEntries = entries.map {
          case (k, v) ⇒
            val value = existing.get(k) match {
              case Some(old) ⇒ v.copy(reconciled = old.reconciled, runtime = old.runtime)
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
      entry.runtime match {
        case cw: ContainerWorkflow ⇒ self ! ReconcileWorkflow(cw.workflow)
        case cs: ContainerService  ⇒ self ! ReconcileService(cs.deployment, cs.service)
        case _                     ⇒
      }
    }

    respond
  }

  private def respond: Map[String, ContainerBufferEntry] ⇒ Unit = { entries ⇒
    val watcher = sender()
    entries.foreach {
      case (_, entry) ⇒ watcher ! entry.runtime
    }
  }

  private def changed(id: String) = {
    store.values.flatMap(_.values).map(_.runtime).find {
      case cs: ContainerService  ⇒ appId(cs.deployment, cs.service.breed) == id
      case cw: ContainerWorkflow ⇒ appId(cw.workflow) == id
      case _                     ⇒ false
    }.foreach {
      case cs: ContainerService  ⇒ self ! ReconcileService(cs.deployment, cs.service)
      case cw: ContainerWorkflow ⇒ self ! ReconcileWorkflow(cw.workflow)
      case _                     ⇒
    }
  }

  private def update(runtime: ContainerRuntime) = {
    val now = OffsetDateTime.now()
    store = store.map {
      case (watcher, entries) ⇒
        watcher → entries.map {
          case (key, value) if value.runtime.getClass == runtime.getClass ⇒

            val cr = value.runtime match {
              case cr: ContainerService ⇒
                val id = appId(cr.deployment, cr.service.breed)
                if (key == id) Option(runtime.asInstanceOf[ContainerService].copy(deployment = cr.deployment, service = cr.service)) else None

              case cr: ContainerWorkflow ⇒
                val id = appId(cr.workflow)
                if (key == id) Option(runtime.asInstanceOf[ContainerWorkflow].copy(workflow = cr.workflow)) else None

              case _ ⇒ None
            }

            cr.map { r ⇒
              watcher ! r
              key → value.copy(runtime = r, reconciled = Option(now))
            } getOrElse (key → value)

          case (key, value) ⇒ key → value
        }
    }
  }

  private def invalidate(id: String) = {
    store = store.map {
      case (watcher, entries) ⇒
        watcher → entries.map {
          case (key, value) if key == id ⇒ key → value.copy(reconciled = None)
          case (key, value)              ⇒ key → value
        }
    }
  }
}
