package io.vamp.pulse

import io.vamp.common.notification.{ DefaultPackageMessageResolverProvider, LoggingNotificationProvider }
import io.vamp.common.{ Namespace, NamespaceProvider }
import io.vamp.model.artifact.{ GlobalReference, ValueReference }
import io.vamp.model.resolver.{ ClassValueResolver, NamespaceValueResolver }

class ElasticsearchValueResolver(implicit val namespace: Namespace)
    extends ClassValueResolver(namespace)
    with ElasticsearchPulseEvent
    with NamespaceValueResolver
    with NamespaceProvider
    with LoggingNotificationProvider
    with DefaultPackageMessageResolverProvider {

  lazy val indexName: String = resolveWithNamespace(ElasticsearchPulseActor.indexName(), lookup = true)

  lazy val indexTimeFormat: Map[String, String] = ElasticsearchPulseActor.indexTimeFormat()

  override def valueForReference(context: AnyRef): PartialFunction[ValueReference, String] = {
    case GlobalReference("es" | "elasticsearch", schema) ⇒
      val (i, t) = indexTypeName(schema, interpolateTime = false)
      s"$i/$t"
  }
}
