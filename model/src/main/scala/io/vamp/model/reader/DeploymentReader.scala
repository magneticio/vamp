package io.vamp.model.reader

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import io.vamp.model.artifact.DeploymentService.State.Step
import io.vamp.model.artifact._
import io.vamp.model.notification.{ NotificationMessageNotRestored, UndefinedStateIntentionError, UndefinedStateStepError }
import io.vamp.model.reader.YamlSourceReader._

import scala.language.postfixOps

trait AbstractDeploymentReader extends YamlReader[Deployment] with TraitReader with ArgumentReader with DialectReader with ReferenceYamlReader[Deployment] {

  override protected def parse(implicit source: YamlSourceReader): Deployment = {
    val clusters = <<?[YamlSourceReader]("clusters") match {
      case None ⇒ List[DeploymentCluster]()
      case Some(yaml) ⇒ yaml.pull().map {
        case (name: String, cluster: YamlSourceReader) ⇒
          implicit val source = cluster
          val sla = SlaReader.readOptionalReferenceOrAnonymous("sla", validateEitherReferenceOrAnonymous)

          <<?[List[YamlSourceReader]]("services") match {
            case None       ⇒ DeploymentCluster(name, Nil, Nil, sla, dialects)
            case Some(list) ⇒ DeploymentCluster(name, list.map(parseService(_)), routingReader.mapping("gateways"), sla, dialects)
          }
      } toList
    }

    Deployment(name, clusters, BlueprintGatewayReader.mapping("gateways"), ports(addGroup = true), environmentVariables, hosts())
  }

  override protected def validate(deployment: Deployment): Deployment = {
    deployment.clusters.foreach(cluster ⇒ validateName(cluster.name))
    deployment
  }

  private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
    case Some(list: List[_]) ⇒ list.map { el ⇒
      implicit val source = el.asInstanceOf[YamlSourceReader]
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

    DeploymentService(state(<<![YamlSourceReader]("state")), breed, environmentVariables(), scale, parseInstances, arguments(), dependencies(), dialects)
  }

  def parseInstances(implicit source: YamlSourceReader): List[DeploymentInstance] = {
    <<?[List[YamlSourceReader]]("instances").map(_.map(parseInstance(_))).getOrElse(Nil)
  }

  private def parseInstance(implicit source: YamlSourceReader): DeploymentInstance = {
    DeploymentInstance(<<![String]("name"), <<![String]("host"), portMapping(), <<![Boolean]("deployed"))
  }

  private def portMapping(name: String = "ports")(implicit source: YamlSourceReader): Map[String, Int] = {
    <<?[YamlSourceReader](name) match {
      case None ⇒ Map()
      case Some(yaml: YamlSourceReader) ⇒ yaml.pull().collect {
        case (key, value: Int)    ⇒ key.toString -> value
        case (key, value: BigInt) ⇒ key.toString -> value.toInt
        case (key, value: String) ⇒ key.toString -> value.toInt
      }
    }
  }

  private def dependencies(name: String = "dependencies")(implicit source: YamlSourceReader): Map[String, String] = {
    <<?[YamlSourceReader](name) match {
      case None ⇒ Map()
      case Some(yaml: YamlSourceReader) ⇒ yaml.pull().collect {
        case (key, value: String) ⇒ key -> value
      }
    }
  }

  private def state(implicit source: YamlSourceReader): DeploymentService.State = DeploymentServiceStateReader.read

  override def readReference: PartialFunction[Any, Deployment] = {
    case yaml: YamlSourceReader if yaml.size > 1 ⇒ read(yaml)
  }

  protected def routingReader: GatewayMappingReader[Gateway]

  protected def validateEitherReferenceOrAnonymous: Boolean = true
}

object DeploymentReader extends AbstractDeploymentReader {
  protected def routingReader: GatewayMappingReader[Gateway] = new InternalGatewayReader(acceptPort = false, onlyAnonymous = true, ignoreError = true)
}

object DeploymentServiceStateReader extends YamlReader[DeploymentService.State] {

  override protected def parse(implicit source: YamlSourceReader) = {
    def since(string: String) = OffsetDateTime.parse(string, DateTimeFormatter.ISO_DATE_TIME)

    val intentionName = <<![String]("intention")
    val intention = DeploymentService.State.Intention.values.find(_.toString == intentionName).getOrElse(
      throwException(UndefinedStateIntentionError(intentionName)))

    val step = <<![String]("step" :: "name") match {
      case n if Step.Failure.getClass.getName.endsWith(s"$n$$") ⇒ Step.Failure(since = since(<<![String]("step" :: "since")), notification = NotificationMessageNotRestored(<<?[String]("step" :: "notification").getOrElse("")))
      case n if Step.Update.getClass.getName.endsWith(s"$n$$") ⇒ Step.Update(since(<<![String]("step" :: "since")))
      case n if Step.Initiated.getClass.getName.endsWith(s"$n$$") ⇒ Step.Initiated(since(<<![String]("step" :: "since")))
      case n if Step.Done.getClass.getName.endsWith(s"$n$$") ⇒ Step.Done(since(<<![String]("step" :: "since")))
      case n ⇒ throwException(UndefinedStateStepError(n))
    }

    DeploymentService.State(intention, step, since(<<![String]("since")))
  }
}
