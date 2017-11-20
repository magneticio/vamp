package io.vamp.persistence

import io.vamp.common.notification.NotificationProvider
import io.vamp.common.{ Artifact, Lookup }
import io.vamp.model.artifact._
import io.vamp.model.reader.YamlSourceReader._
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
    Template.kind → TemplateReader,
    // gateway persistence
    RouteTargets.kind → new NoNameValidationYamlReader[RouteTargets] {
      override protected def parse(implicit source: YamlSourceReader): RouteTargets = {
        val targets = <<?[YamlList]("targets") match {
          case Some(list) ⇒ list.flatMap { yaml ⇒
            implicit val source: YamlSourceReader = yaml
            (<<?[String]("name"), <<?[String]("url")) match {
              case (_, Some(url)) ⇒ ExternalRouteTarget(url, metadataAsRootAnyMap) :: Nil
              case (Some(name), _) ⇒
                source.find[Map[_, _]](Artifact.metadata)
                InternalRouteTarget(name, <<?[String]("host"), <<![Int]("port")) :: Nil
              case _ ⇒ Nil
            }
          }
          case _ ⇒ Nil
        }
        RouteTargets(<<![String]("name"), targets)
      }
    },
    GatewayPort.kind → new NoNameValidationYamlReader[GatewayPort] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayPort(name, <<![Int]("port"))
    },
    GatewayServiceAddress.kind → new NoNameValidationYamlReader[GatewayServiceAddress] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayServiceAddress(name, <<![String]("host"), <<![Int]("port"))
    },
    GatewayDeploymentStatus.kind → new NoNameValidationYamlReader[GatewayDeploymentStatus] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayDeploymentStatus(name, <<![Boolean]("deployed"))
    },
    InternalGateway.kind → new NoNameValidationYamlReader[InternalGateway] {
      override protected def parse(implicit source: YamlSourceReader): InternalGateway = {
        <<?[Any]("name")
        <<?[Any]("gateway" :: Lookup.entry :: Nil)
        InternalGateway(DeployedGatewayReader.read(<<![YamlSourceReader]("gateway")))
      }
    },
    // deployment persistence
    DeploymentServiceStatus.kind → new NoNameValidationYamlReader[DeploymentServiceStatus] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceStatus(name, DeploymentServiceStatusReader.read(<<![YamlSourceReader]("status")))
    },
    DeploymentServiceScale.kind → new NoNameValidationYamlReader[DeploymentServiceScale] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceScale(name, ScaleReader.read(<<![YamlSourceReader]("scale")).asInstanceOf[DefaultScale])
    },
    DeploymentServiceInstances.kind → new NoNameValidationYamlReader[DeploymentServiceInstances] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceInstances(name, DeploymentReader.parseInstances)
    },
    DeploymentServiceHealth.kind → new NoNameValidationYamlReader[DeploymentServiceHealth] {
      override protected def parse(implicit source: YamlSourceReader) =
        DeploymentServiceHealth(name, HealthReader.health(<<![YamlSourceReader]("health")))
    },
    DeploymentServiceEnvironmentVariables.kind → new NoNameValidationYamlReader[DeploymentServiceEnvironmentVariables] {

      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceEnvironmentVariables(name, environmentVariables)

      private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
        case Some(list: List[_]) ⇒ list.map { el ⇒
          implicit val source: YamlSourceReader = el.asInstanceOf[YamlSourceReader]
          EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
        }
        case _ ⇒ Nil
      }
    },
    // workflow persistence
    WorkflowBreed.kind → new NoNameValidationYamlReader[WorkflowBreed] {
      override protected def parse(implicit source: YamlSourceReader): WorkflowBreed = WorkflowBreed(name, BreedReader.read(<<![YamlSourceReader]("breed")).asInstanceOf[DefaultBreed])
    },
    WorkflowStatus.kind → new NoNameValidationYamlReader[WorkflowStatus] {
      override protected def parse(implicit source: YamlSourceReader): WorkflowStatus = {
        val status = <<![String]("status")
        WorkflowStatusReader.status(status) match {
          case _: Workflow.Status.Restarting ⇒ WorkflowStatus(name, status, <<?[String]("phase"))
          case _                             ⇒ WorkflowStatus(name, status, None)
        }
      }
    },
    WorkflowScale.kind → new NoNameValidationYamlReader[WorkflowScale] {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowScale(name, ScaleReader.read(<<![YamlSourceReader]("scale")).asInstanceOf[DefaultScale])
    },
    WorkflowNetwork.kind → new NoNameValidationYamlReader[WorkflowNetwork] {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowNetwork(name, <<![String]("network"))
    },
    WorkflowArguments.kind → new NoNameValidationYamlReader[WorkflowArguments] with ArgumentReader {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowArguments(name, arguments())
    },
    WorkflowInstances.kind → new NoNameValidationYamlReader[WorkflowInstances] with ArgumentReader {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowInstances(name, DeploymentReader.parseInstances)
    },
    WorkflowHealth.kind → new NoNameValidationYamlReader[WorkflowHealth] with ArgumentReader {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowHealth(name, <<?[YamlSourceReader]("health").map(HealthReader.health(_)))
    },
    WorkflowEnvironmentVariables.kind → new NoNameValidationYamlReader[WorkflowEnvironmentVariables] {

      override protected def parse(implicit source: YamlSourceReader) = WorkflowEnvironmentVariables(name, environmentVariables)

      private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
        case Some(list: List[_]) ⇒ list.map { el ⇒
          implicit val source: YamlSourceReader = el.asInstanceOf[YamlSourceReader]
          EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
        }
        case _ ⇒ Nil
      }
    }
  )
}
