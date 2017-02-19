package io.vamp.persistence

import io.vamp.common.notification.NotificationProvider
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

  def marshall(input: AnyRef): String = {
    write(input)(CoreSerializationFormat.full)
  }

  def unmarshall[T <: Artifact: ClassTag](source: String): List[T] = {
    ArtifactListReader.read(source).flatMap { item ⇒
      Try(readers.get(type2string(classTag[T].runtimeClass)).map(_.read(item.source).asInstanceOf[T])).getOrElse(None)
    }
  }

  def unmarshall(`type`: String, source: String): Option[Artifact] = {
    Try(readers.get(`type`).map(_.read(source))).getOrElse(None)
  }

  private val readers = Map(
    "gateways" → DeployedGatewayReader,
    "deployments" → new AbstractDeploymentReader() {
      override protected def routingReader = new InternalGatewayReader(acceptPort = true, onlyAnonymous = false)

      override protected def validateEitherReferenceOrAnonymous = false
    },
    "breeds" → BreedReader,
    "blueprints" → BlueprintReader,
    "slas" → SlaReader,
    "scales" → ScaleReader,
    "escalations" → EscalationReader,
    "routes" → RouteReader,
    "conditions" → ConditionReader,
    "rewrites" → RewriteReader,
    "workflows" → WorkflowReader,
    // gateway persistence
    "route-targets" → new NoNameValidationYamlReader[RouteTargets] {
      override protected def parse(implicit source: YamlSourceReader) = {
        val targets = <<?[YamlList]("targets") match {
          case Some(list) ⇒ list.flatMap { yaml ⇒
            implicit val source = yaml
            (<<?[String]("name"), <<?[String]("url")) match {
              case (_, Some(url)) ⇒ ExternalRouteTarget(url, metadata) :: Nil
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
    "gateway-ports" → new NoNameValidationYamlReader[GatewayPort] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayPort(name, <<![Int]("port"))
    },
    "gateway-services" → new NoNameValidationYamlReader[GatewayServiceAddress] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayServiceAddress(name, <<![String]("host"), <<![Int]("port"))
    },
    "gateway-deployment-statuses" → new NoNameValidationYamlReader[GatewayDeploymentStatus] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayDeploymentStatus(name, <<![Boolean]("deployed"))
    },
    "internal-gateway" → new NoNameValidationYamlReader[InternalGateway] {
      override protected def parse(implicit source: YamlSourceReader) = {
        <<?[Any]("name")
        <<?[Any]("gateway" :: Lookup.entry :: Nil)
        InternalGateway(DeployedGatewayReader.read(<<![YamlSourceReader]("gateway")))
      }
    },
    // deployment persistence
    "deployment-service-statuses" → new NoNameValidationYamlReader[DeploymentServiceStatus] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceStatus(name, DeploymentServiceStatusReader.read(<<![YamlSourceReader]("status")))
    },
    "deployment-service-scales" → new NoNameValidationYamlReader[DeploymentServiceScale] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceScale(name, ScaleReader.read(<<![YamlSourceReader]("scale")).asInstanceOf[DefaultScale])
    },
    "deployment-service-instances" → new NoNameValidationYamlReader[DeploymentServiceInstances] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceInstances(name, DeploymentReader.parseInstances)
    },
    "deployment-service-health" → new NoNameValidationYamlReader[DeploymentServiceHealth] {
      override protected def parse(implicit source: YamlSourceReader) =
        DeploymentServiceHealth(name, ServiceHealthReader.serviceHealth(<<![YamlSourceReader]("service_health")))
    },
    "deployment-service-environment-variables" → new NoNameValidationYamlReader[DeploymentServiceEnvironmentVariables] {

      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceEnvironmentVariables(name, environmentVariables)

      private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
        case Some(list: List[_]) ⇒ list.map { el ⇒
          implicit val source = el.asInstanceOf[YamlSourceReader]
          EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
        }
        case _ ⇒ Nil
      }
    },
    // workflow persistence
    "workflow-breed" → new NoNameValidationYamlReader[WorkflowBreed] {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowBreed(name, BreedReader.read(<<![YamlSourceReader]("breed")).asInstanceOf[DefaultBreed])
    },
    "workflow-status" → new NoNameValidationYamlReader[WorkflowStatus] {
      override protected def parse(implicit source: YamlSourceReader) = {
        val status = <<![String]("status")
        WorkflowStatusReader.status(status) match {
          case _: Workflow.Status.Restarting ⇒ WorkflowStatus(name, status, <<?[String]("phase"))
          case _                             ⇒ WorkflowStatus(name, status, None)
        }
      }
    },
    "workflow-scale" → new NoNameValidationYamlReader[WorkflowScale] {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowScale(name, ScaleReader.read(<<![YamlSourceReader]("scale")).asInstanceOf[DefaultScale])
    },
    "workflow-network" → new NoNameValidationYamlReader[WorkflowNetwork] {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowNetwork(name, <<![String]("network"))
    },
    "workflow-arguments" → new NoNameValidationYamlReader[WorkflowArguments] with ArgumentReader {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowArguments(name, arguments())
    },
    "workflow-instances" → new NoNameValidationYamlReader[WorkflowInstances] with ArgumentReader {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowInstances(name, DeploymentReader.parseInstances)
    },
    "workflow-environment-variables" → new NoNameValidationYamlReader[WorkflowEnvironmentVariables] {

      override protected def parse(implicit source: YamlSourceReader) = WorkflowEnvironmentVariables(name, environmentVariables)

      private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
        case Some(list: List[_]) ⇒ list.map { el ⇒
          implicit val source = el.asInstanceOf[YamlSourceReader]
          EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
        }
        case _ ⇒ Nil
      }
    }
  )
}
