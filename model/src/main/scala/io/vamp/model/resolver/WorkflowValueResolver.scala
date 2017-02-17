package io.vamp.model.resolver

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._

trait WorkflowValueResolver extends ValueResolver {
  this: NotificationProvider ⇒

  def resolveEnvironmentVariable(workflow: Workflow): EnvironmentVariable ⇒ EnvironmentVariable = { env ⇒
    env.copy(interpolated = env.value.map(value ⇒ resolve(value, valueFor(workflow))))
  }

  private def valueFor(workflow: Workflow)(reference: ValueReference): String = (
    valueForWorkflow(workflow)
    orElse GlobalValueResolver.valueForReference
    orElse PartialFunction[ValueReference, String] { _ ⇒ "" }
  )(reference)

  private def valueForWorkflow(workflow: Workflow): PartialFunction[ValueReference, String] = {
    case LocalReference("workflow")           ⇒ workflow.name
    case NoGroupReference("workflow", "name") ⇒ workflow.name
  }
}
