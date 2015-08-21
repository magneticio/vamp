package io.vamp.core.operation.workflow

import akka.actor.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.http.InfoMessageBase
import io.vamp.common.vitals.JvmVitals
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.operation.controller.InfoController

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

case class InfoMessage(message: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any) extends InfoMessageBase

class InfoContext(actorContext: ActorContext)(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext {

  implicit lazy val timeout: Timeout = Timeout(ConfigFactory.load().getInt("vamp.core.operation.workflow.info.timeout") seconds)

  def info() = serialize {
    async {
      await {
        new InfoController with ExecutionContextProvider {
          override def actorContext: ActorContext = InfoContext.this.actorContext

          override implicit def timeout: Timeout = InfoContext.this.timeout

          override implicit def executionContext: ExecutionContext = InfoContext.this.executionContext
        } info
      }
    }
  }
}
