package io.vamp.model.resolver

import io.vamp.common.NamespaceProvider
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._

trait WorkflowValueResolver extends ValueResolver with ConfigurationValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  def valueFor(workflow: Workflow)(reference: ValueReference): String = {
    (valueForWorkflow(workflow) orElse PartialFunction[ValueReference, String] { _ ⇒ "" })(reference)
  }

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
    case LocalReference("workflow")                                       ⇒ workflow.name
    case LocalReference("namespace")                                      ⇒ namespace.name
    case LocalReference(ref) if workflow.breed.isInstanceOf[DefaultBreed] ⇒ workflow.breed.asInstanceOf[DefaultBreed].traits.find(_.name == ref).flatMap(_.value).getOrElse("")
    case NoGroupReference("workflow", "name")                             ⇒ workflow.name
  }
}
