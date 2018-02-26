package io.vamp.persistence

import io.vamp.common.Artifact
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.reader._
import io.vamp.model.serialization.CoreSerializationFormat
import org.json4s.native.Serialization._

import scala.reflect._
import scala.util.Try

trait NoNameValidationYamlReader[T] extends YamlReader[T] {

  import YamlSourceReader._

  override protected def name(implicit source: YamlSourceReader): String = <<![String]("name")
}

trait PersistenceMarshaller extends TypeOfArtifact {
  this: NotificationProvider ⇒

  def marshall(input: AnyRef): String = write(input)(CoreSerializationFormat.full)

  def unmarshall[T <: Artifact: ClassTag](source: String): List[T] = {
    ArtifactListReader.read(source).flatMap { item ⇒
      Try(readers.get(type2string(classTag[T].runtimeClass)).map(_.read(item.source).asInstanceOf[T])).getOrElse(None)
    }
  }

  def unmarshall(`type`: String, source: String): Option[Artifact] = Try(readers.get(`type`).map(_.read(source))).getOrElse(None)

  protected lazy val readers: Map[String, YamlReader[_ <: Artifact]] = Map(
    Gateway.kind → DeployedGatewayReader,
    Deployment.kind → new AbstractDeploymentReader() {
      override protected def routingReader = new InternalGatewayReader(acceptPort = true, onlyAnonymous = false)

      override protected def validateEitherReferenceOrAnonymous = false
    },
    Breed.kind → BreedReader,
    Blueprint.kind → BlueprintReader,
    Sla.kind → SlaReader,
    Scale.kind → ScaleReader,
    Escalation.kind → EscalationReader,
    Route.kind → RouteReader,
    Condition.kind → ConditionReader,
    Rewrite.kind → RewriteReader,
    Workflow.kind → WorkflowReader,
    Template.kind → TemplateReader
  )
}
