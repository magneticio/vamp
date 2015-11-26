package io.vamp.model.reader

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import io.vamp.model.artifact.DeploymentService.State.Step
import io.vamp.model.artifact._
import io.vamp.model.notification.{ NotificationMessageNotRestored, UndefinedStateIntentionError, UndefinedStateStepError }
import io.vamp.model.reader.YamlSourceReader._

import scala.language.postfixOps

object DeploymentReader extends YamlReader[Deployment] with TraitReader with DialectReader {

  override protected def parse(implicit source: YamlSourceReader): Deployment = {
    val clusters = <<?[YamlSourceReader]("clusters") match {
      case None ⇒ List[DeploymentCluster]()
      case Some(yaml) ⇒ yaml.pull().map {
        case (name: String, cluster: YamlSourceReader) ⇒
          implicit val source = cluster
          val sla = SlaReader.readOptionalReferenceOrAnonymous("sla")

          <<?[List[YamlSourceReader]]("services") match {
            case None       ⇒ DeploymentCluster(name, Nil, sla, portMapping("routes"), dialects)
            case Some(list) ⇒ DeploymentCluster(name, list.map(parseService(_)), sla, portMapping("routes"), dialects)
          }
      } toList
    }

    Deployment(name, clusters, ports("endpoints", addGroup = true), ports(addGroup = true), environmentVariables, hosts())
  }

  private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
    case Some(list: List[_]) ⇒ list.map { el ⇒
      implicit val source = el.asInstanceOf[YamlSourceReader]
      EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
    }
    case _ ⇒ Nil
  }

  private def parseService(implicit source: YamlSourceReader): DeploymentService = {
    val breed = BreedReader.readReference(<<![Any]("breed")).asInstanceOf[DefaultBreed]
    val scale = ScaleReader.readOptionalReferenceOrAnonymous("scale").asInstanceOf[Option[DefaultScale]]
    val routing = RoutingReader.readOptionalReferenceOrAnonymous("routing").asInstanceOf[Option[DefaultRouting]]
    val servers = <<?[List[YamlSourceReader]]("servers") match {
      case None       ⇒ Nil
      case Some(list) ⇒ list.map(parseServer(_))
    }

    DeploymentService(state(<<![YamlSourceReader]("state")), breed, environmentVariables(), scale, routing, servers, dependencies(), dialects)
  }

  private def parseServer(implicit source: YamlSourceReader): DeploymentServer =
    DeploymentServer(name, <<![String]("host"), portMapping(), <<![Boolean]("deployed"))

  private def portMapping(name: String = "ports")(implicit source: YamlSourceReader): Map[Int, Int] = {
    <<?[YamlSourceReader](name) match {
      case None ⇒ Map()
      case Some(yaml: YamlSourceReader) ⇒ yaml.pull().flatMap {
        case (key, value: Int)    ⇒ (key.toInt -> value) :: Nil
        case (key, value: BigInt) ⇒ (key.toInt -> value.toInt) :: Nil
        case (key, value: String) ⇒ (key.toInt -> value.toInt) :: Nil
        case _                    ⇒ Nil
      }
    }
  }

  private def dependencies(name: String = "dependencies")(implicit source: YamlSourceReader): Map[String, String] = {
    <<?[YamlSourceReader](name) match {
      case None ⇒ Map()
      case Some(yaml: YamlSourceReader) ⇒ yaml.pull().flatMap {
        case (key, value: String) ⇒ (key -> value) :: Nil
        case _                    ⇒ Nil
      }
    }
  }

  private def state(implicit source: YamlSourceReader): DeploymentService.State = {
    def since(string: String) = OffsetDateTime.parse(string, DateTimeFormatter.ISO_DATE_TIME)

    val intentionName = <<![String]("intention")
    val intention = DeploymentService.State.Intention.values.find(_.toString == intentionName).getOrElse(
      throwException(UndefinedStateIntentionError(intentionName)))

    val step = <<![String]("step" :: "name") match {
      case n if Step.Failure.getClass.getName.endsWith(s"$n$$") ⇒ Step.Failure(since = since(<<![String]("step" :: "since")), notification = NotificationMessageNotRestored(<<?[String]("step" :: "message").getOrElse("")))
      case n if Step.ContainerUpdate.getClass.getName.endsWith(s"$n$$") ⇒ Step.ContainerUpdate(since(<<![String]("step" :: "since")))
      case n if Step.RouteUpdate.getClass.getName.endsWith(s"$n$$") ⇒ Step.RouteUpdate(since(<<![String]("step" :: "since")))
      case n if Step.Initiated.getClass.getName.endsWith(s"$n$$") ⇒ Step.Initiated(since(<<![String]("step" :: "since")))
      case n if Step.Done.getClass.getName.endsWith(s"$n$$") ⇒ Step.Done(since(<<![String]("step" :: "since")))
      case n ⇒ throwException(UndefinedStateStepError(n))
    }

    DeploymentService.State(intention, step, since(<<![String]("since")))
  }
}
