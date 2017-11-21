package io.vamp.model.reader

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import io.vamp.common.Artifact
import io.vamp.model.artifact.DeploymentService.Status.Phase
import io.vamp.model.artifact._
import io.vamp.model.notification.{ NotificationMessageNotRestored, UndefinedStateIntentionError, UndefinedStateStepError }
import io.vamp.model.reader.YamlSourceReader._

import scala.language.postfixOps

trait AbstractDeploymentReader
    extends YamlReader[Deployment]
    with TraitReader
    with ArgumentReader
    with DialectReader
    with ReferenceYamlReader[Deployment] {

  override protected def parse(implicit source: YamlSourceReader): Deployment = {
    val clusters = <<?[YamlSourceReader]("clusters") match {
      case None ⇒ List[DeploymentCluster]()
      case Some(yaml) ⇒ yaml.pull().collect {
        case (name: String, cluster: YamlSourceReader) ⇒
          implicit val source: YamlSourceReader = cluster
          val sla = SlaReader.readOptionalReferenceOrAnonymous("sla", validateEitherReferenceOrAnonymous)

          <<?[List[YamlSourceReader]]("services") match {
            case None ⇒
              DeploymentCluster(name, metadataAsRootAnyMap, Nil, Nil, HealthCheckReader.read, <<?[String]("network"), sla, dialectsAsAnyRootMap)
            case Some(list) ⇒
              DeploymentCluster(
                name,
                metadataAsRootAnyMap,
                list.map(parseService(_)),
                routingReader.mapping("gateways"),
                HealthCheckReader.read,
                <<?[String]("network"),
                sla,
                dialectsAsAnyRootMap
              )
          }
      } toList
    }

    Deployment(name, metadataAsRootAnyMap, clusters, BlueprintGatewayReader.mapping("gateways"), ports(addGroup = true), environmentVariables, hosts(), dialects)
  }

  override protected def validate(deployment: Deployment): Deployment = {
    deployment.clusters.foreach(cluster ⇒ validateName(cluster.name))
    deployment
  }

  private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
    case Some(list: List[_]) ⇒ list.map { el ⇒
      implicit val source: YamlSourceReader = el.asInstanceOf[YamlSourceReader]
      EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
    }
    case Some(input: YamlSourceReader) ⇒
      input.pull()
      Nil
    case _ ⇒ Nil
  }

  private def parseService(implicit source: YamlSourceReader): DeploymentService = {
    val breed = BreedReader.readReference(<<![Any]("breed")).asInstanceOf[DefaultBreed]
    val scale = ScaleReader.readOptionalReferenceOrAnonymous("scale", validateEitherReferenceOrAnonymous).asInstanceOf[Option[DefaultScale]]

    DeploymentService(
      status(<<![YamlSourceReader]("status")),
      breed,
      environmentVariables(),
      scale,
      parseInstances,
      arguments(),
      HealthCheckReader.read,
      <<?[String]("network"), dependencies(),
      dialectsAsAnyRootMap,
      HealthReader.read
    )
  }

  def parseInstances(implicit source: YamlSourceReader): List[Instance] = {
    <<?[List[YamlSourceReader]]("instances").map(_.map(parseInstance(_))).getOrElse(Nil)
  }

  private def parseInstance(implicit source: YamlSourceReader): Instance = {
    source.find[Map[_, _]](Artifact.metadata)
    Instance(<<![String]("name"), <<![String]("host"), portMapping(), <<![Boolean]("deployed"))
  }

  private def portMapping(name: String = "ports")(implicit source: YamlSourceReader): Map[String, Int] = {
    <<?[YamlSourceReader](name) match {
      case None ⇒ Map()
      case Some(yaml: YamlSourceReader) ⇒ yaml.pull().collect {
        case (key, value: Int)    ⇒ key.toString → value
        case (key, value: BigInt) ⇒ key.toString → value.toInt
        case (key, value: String) ⇒ key.toString → value.toInt
      }
    }
  }

  private def dependencies(name: String = "dependencies")(implicit source: YamlSourceReader): Map[String, String] = {
    <<?[YamlSourceReader](name) match {
      case None ⇒ Map()
      case Some(yaml: YamlSourceReader) ⇒ yaml.pull().collect {
        case (key, value: String) ⇒ key → value
      }
    }
  }

  private def status(implicit source: YamlSourceReader): DeploymentService.Status = DeploymentServiceStatusReader.read

  override def readReference: PartialFunction[Any, Deployment] = {
    case yaml: YamlSourceReader if yaml.size > 1 ⇒ read(yaml)
  }

  protected def routingReader: GatewayMappingReader[Gateway]

  protected def validateEitherReferenceOrAnonymous: Boolean = true
}

object DeploymentReader extends AbstractDeploymentReader {
  protected def routingReader: GatewayMappingReader[Gateway] = new InternalGatewayReader(acceptPort = false, onlyAnonymous = true, ignoreError = true)
}

object DeploymentServiceStatusReader extends YamlReader[DeploymentService.Status] {

  override protected def parse(implicit source: YamlSourceReader) = {
    def since(string: String) = OffsetDateTime.parse(string, DateTimeFormatter.ISO_DATE_TIME)

    val intentionName = <<![String]("intention")
    val intention = DeploymentService.Status.Intention.values.find(_.toString == intentionName).getOrElse(
      throwException(UndefinedStateIntentionError(intentionName))
    )

    val phase = <<![String]("phase" :: "name") match {
      case n if Phase.Failed.getClass.getName.endsWith(s"$n$$") ⇒ Phase.Failed(since = since(<<![String]("phase" :: "since")), notificationMessage = NotificationMessageNotRestored(<<?[String]("phase" :: "notification").getOrElse("")).message)
      case n if Phase.Updating.getClass.getName.endsWith(s"$n$$") ⇒ Phase.Updating(since(<<![String]("phase" :: "since")))
      case n if Phase.Initiated.getClass.getName.endsWith(s"$n$$") ⇒ Phase.Initiated(since(<<![String]("phase" :: "since")))
      case n if Phase.Done.getClass.getName.endsWith(s"$n$$") ⇒ Phase.Done(since(<<![String]("phase" :: "since")))
      case n ⇒ throwException(UndefinedStateStepError(n))
    }

    DeploymentService.Status(intention, phase, since(<<![String]("since")))
  }
}

object HealthReader extends YamlReader[Option[Health]] {

  def health(implicit yamlSourceReader: YamlSourceReader): Health =
    Health(
      <<![Int]("staged"),
      <<![Int]("running"),
      <<![Int]("healthy"),
      <<![Int]("unhealthy")
    )

  override protected def parse(implicit source: YamlSourceReader): Option[Health] =
    <<?[YamlSourceReader]("health").map(ysr ⇒ health(ysr))
}
