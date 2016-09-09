package io.vamp.persistence.db

import io.vamp.model.artifact._
import io.vamp.model.reader._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.serialization.CoreSerializationFormat
import org.json4s.native.Serialization._

import scala.util.Try

trait NoNameValidationYamlReader[T] extends YamlReader[T] {

  import YamlSourceReader._

  override protected def name(implicit source: YamlSourceReader): String = <<![String]("name")
}

trait PersistenceMarshaller {

  def marshall(artifact: Artifact): String = {
    write(artifact)(CoreSerializationFormat.full)
  }

  def unmarshall(`type`: String, source: String): Option[Artifact] = {
    Try(readers.get(`type`).map(_.read(source))).getOrElse(None)
  }

  private val readers = Map(
    "gateways" -> DeployedGatewayReader,
    "deployments" -> new AbstractDeploymentReader() {
      override protected def routingReader = new InnerGatewayReader(acceptPort = true, onlyAnonymous = false)

      override protected def validateEitherReferenceOrAnonymous = false
    },
    "breeds" -> BreedReader,
    "blueprints" -> BlueprintReader,
    "slas" -> SlaReader,
    "scales" -> ScaleReader,
    "escalations" -> EscalationReader,
    "routes" -> RouteReader,
    "conditions" -> ConditionReader,
    "rewrites" -> RewriteReader,
    "workflows" -> SilentWorkflowReader,
    // gateway persistence
    "route-targets" -> new NoNameValidationYamlReader[RouteTargets] {
      override protected def parse(implicit source: YamlSourceReader) = {
        val targets = <<?[YamlList]("targets") match {
          case Some(list) ⇒ list.flatMap { yaml ⇒
            implicit val source = yaml
            (<<?[String]("name"), <<?[String]("url")) match {
              case (_, Some(url))  ⇒ ExternalRouteTarget(url) :: Nil
              case (Some(name), _) ⇒ InternalRouteTarget(name, <<?[String]("host"), <<![Int]("port")) :: Nil
              case _               ⇒ Nil
            }
          }
          case _ ⇒ Nil
        }
        RouteTargets(<<![String]("name"), targets)
      }
    },
    "gateway-ports" -> new NoNameValidationYamlReader[GatewayPort] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayPort(name, <<![Int]("port"))
    },
    "gateway-services" -> new NoNameValidationYamlReader[GatewayServiceAddress] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayServiceAddress(name, <<![String]("host"), <<![Int]("port"))
    },
    "gateway-deployment-statuses" -> new NoNameValidationYamlReader[GatewayDeploymentStatus] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayDeploymentStatus(name, <<![Boolean]("deployed"))
    },
    "inner-gateway" -> new NoNameValidationYamlReader[InnerGateway] {
      override protected def parse(implicit source: YamlSourceReader) = {
        <<?[Any]("name")
        <<?[Any]("gateway" :: Lookup.entry :: Nil)
        InnerGateway(DeployedGatewayReader.read(<<![YamlSourceReader]("gateway")))
      }
    },
    // deployment persistence
    "deployment-service-states" -> new NoNameValidationYamlReader[DeploymentServiceState] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceState(name, DeploymentServiceStateReader.read(<<![YamlSourceReader]("state")))
    },
    "deployment-service-scales" -> new NoNameValidationYamlReader[DeploymentServiceScale] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceScale(name, ScaleReader.read(<<![YamlSourceReader]("scale")).asInstanceOf[DefaultScale])
    },
    "deployment-service-instances" -> new NoNameValidationYamlReader[DeploymentServiceInstances] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceInstances(name, DeploymentReader.parseInstances)
    },
    "deployment-service-environment-variables" -> new NoNameValidationYamlReader[DeploymentServiceEnvironmentVariables] {

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
    "workflow-network" -> new NoNameValidationYamlReader[WorkflowNetwork] {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowNetwork(name, <<![String]("network"))
    },
    "workflow-arguments" -> new NoNameValidationYamlReader[WorkflowArguments] with ArgumentReader {
      override protected def parse(implicit source: YamlSourceReader) = WorkflowArguments(name, arguments())
    }
  )
}
