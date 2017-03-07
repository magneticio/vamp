package io.vamp.model.resolver

import io.vamp.common.NamespaceProvider
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._

trait WorkflowValueResolver extends ValueResolver with ConfigurationValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  def resolveEnvironmentVariable(workflow: Workflow): EnvironmentVariable ⇒ EnvironmentVariable = { env ⇒
    env.copy(interpolated = env.value.map { value ⇒
      resolve(
        resolve(
          value,
          valueFor(workflow, super[ConfigurationValueResolver].valueForReference)
        ),
        valueFor(workflow, valueForWorkflow(workflow: Workflow))
      )
    })
  }

  private def valueFor(workflow: Workflow, resolver: PartialFunction[ValueReference, String])(reference: ValueReference): String = {
    (resolver orElse PartialFunction[ValueReference, String] { _ ⇒ "" })(reference)
  }

  private def valueForWorkflow(workflow: Workflow): PartialFunction[ValueReference, String] = {
    case LocalReference("workflow")           ⇒ workflow.name
    case LocalReference("namespace")          ⇒ namespace.name
    case NoGroupReference("workflow", "name") ⇒ workflow.name
  }
}
