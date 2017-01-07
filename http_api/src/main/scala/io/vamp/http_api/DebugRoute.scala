package io.vamp.http_api

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.http.scaladsl.model.StatusCodes._
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, IoC }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.operation.deployment.DeploymentSynchronizationActor
import io.vamp.operation.gateway.GatewaySynchronizationActor
import io.vamp.operation.sla.{ EscalationActor, SlaActor }
import io.vamp.operation.workflow.WorkflowSynchronizationActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait DebugRoute extends DebugController {
  this: ExecutionContextProvider with ActorSystemProvider with HttpApiDirectives with NotificationProvider ⇒

  implicit def timeout: Timeout

  val debugRoutes = {
    pathPrefix("sync") {
      onComplete(sync()) { _ ⇒
        complete(Accepted)
      }
    } ~ path("sla") {
      onComplete(slaCheck()) { _ ⇒
        complete(Accepted)
      }
    } ~ path("escalation") {
      onComplete(slaEscalation()) { _ ⇒
        complete(Accepted)
      }
    } ~ path("reload") {
      onComplete(reload()) { _ ⇒
        complete(Accepted)
      }
    }
  }
}

trait DebugController {
  this: NotificationProvider with ExecutionContextProvider with ActorSystemProvider ⇒

  def sync() = Future.successful {
    actorSystem.scheduler.scheduleOnce(0 second, () ⇒ {
      IoC.actorFor[DeploymentSynchronizationActor] ! DeploymentSynchronizationActor.SynchronizeAll
    })
    actorSystem.scheduler.scheduleOnce(1 second, () ⇒ {
      IoC.actorFor[GatewaySynchronizationActor] ! GatewaySynchronizationActor.SynchronizeAll
    })
    actorSystem.scheduler.scheduleOnce(2 second, () ⇒ {
      IoC.actorFor[WorkflowSynchronizationActor] ! WorkflowSynchronizationActor.SynchronizeAll
    })
  }

  def slaCheck() = Future.successful {
    IoC.actorFor[SlaActor] ! SlaActor.SlaProcessAll
  }

  def slaEscalation() = Future.successful {
    val now = OffsetDateTime.now()
    IoC.actorFor[EscalationActor] ! EscalationActor.EscalationProcessAll(now.minus(1, ChronoUnit.HOURS), now)
  }

  def reload() = Future.successful {
    actorSystem.actorSelection("/user/vamp") ! "reload"
  }
}
