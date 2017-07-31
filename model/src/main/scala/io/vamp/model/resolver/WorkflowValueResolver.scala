package io.vamp.model.resolver

import io.vamp.common.{ Config, NamespaceProvider }
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.common.notification.NotificationProvider
import io.vamp.common.util.TextUtil
import io.vamp.model.artifact._
import org.json4s.native.Serialization.write

trait WorkflowValueResolver extends ValueResolver with ConfigurationValueResolver with ClassLoaderValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  private val resolversPath = "vamp.model.resolvers.workflow"

  override def resolverClasses: List[String] = if (Config.has(resolversPath)(namespace)()) Config.stringList(resolversPath)() else Nil

  def valueFor(workflow: Workflow)(reference: ValueReference): String = {
    (valueForWorkflow(workflow, None) orElse PartialFunction[ValueReference, String] { _ ⇒ "" })(reference)
  }

  def resolveEnvironmentVariable(workflow: Workflow, data: Any): EnvironmentVariable ⇒ EnvironmentVariable = { env ⇒
    env.copy(interpolated = env.value.map { value ⇒
      resolve(
        resolve(
          value,
          super[ClassLoaderValueResolver].valueForReference((workflow, data)) orElse
            super[ConfigurationValueResolver].valueForReference orElse
            PartialFunction[ValueReference, String] { referenceAsPart }
        ),
        valueForWorkflow(workflow: Workflow, data) orElse PartialFunction[ValueReference, String] { _ ⇒ "" }
      )
    })
  }

  override def valueForReference: PartialFunction[ValueReference, String] = PartialFunction.empty

  private def valueForWorkflow(workflow: Workflow, data: Any): PartialFunction[ValueReference, String] = {
    case LocalReference("data") ⇒ TextUtil.encodeBase64(write(data.asInstanceOf[AnyRef])(SerializationFormat(OffsetDateTimeSerializer)))
    case LocalReference("workflow") ⇒ workflow.name
    case LocalReference("namespace") ⇒ namespace.name
    case LocalReference(ref) if workflow.breed.isInstanceOf[DefaultBreed] ⇒ workflow.breed.asInstanceOf[DefaultBreed].traits.find(_.name == ref).flatMap(_.value).getOrElse("")
    case NoGroupReference("workflow", "name") ⇒ workflow.name
  }
}
