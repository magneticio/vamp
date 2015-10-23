package io.vamp.model.reader

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import io.vamp.model.artifact.DeploymentService.State.Step
import io.vamp.model.artifact._
import io.vamp.model.notification.{ NotificationMessageNotRestored, UndefinedStateIntentionError, UndefinedStateStepError }

import scala.language.postfixOps

object DeploymentReader extends YamlReader[Deployment] with TraitReader with DialectReader {

  override protected def parse(implicit source: DeploymentReader.YamlObject): Deployment = {
    val clusters = <<?[YamlObject]("clusters") match {
      case None ⇒ List[DeploymentCluster]()
      case Some(map) ⇒ map.map({
        case (name: String, cluster: collection.Map[_, _]) ⇒
          implicit val source = cluster.asInstanceOf[YamlObject]
          val sla = SlaReader.readOptionalReferenceOrAnonymous("sla")

          <<?[List[YamlObject]]("services") match {
            case None       ⇒ DeploymentCluster(name, Nil, sla, portMapping("routes"), dialects)
            case Some(list) ⇒ DeploymentCluster(name, list.map(parseService(_)), sla, portMapping("routes"), dialects)
          }
      }).toList
    }

    Deployment(name, clusters, ports("endpoints", addGroup = true), ports(addGroup = true), environmentVariables, hosts())
  }

  private def environmentVariables(implicit source: YamlObject): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
    case Some(list: List[_]) ⇒ list.map { el ⇒
      implicit val source = el.asInstanceOf[YamlObject]
      EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
    }
    case _ ⇒ Nil
  }

  private def parseService(implicit source: YamlObject): DeploymentService = {
    val breed = BreedReader.readReference(<<![Any]("breed")).asInstanceOf[DefaultBreed]
    val scale = ScaleReader.readOptionalReferenceOrAnonymous("scale").asInstanceOf[Option[DefaultScale]]
    val routing = RoutingReader.readOptionalReferenceOrAnonymous("routing").asInstanceOf[Option[DefaultRouting]]
    val servers = <<?[List[YamlObject]]("servers") match {
      case None       ⇒ Nil
      case Some(list) ⇒ list.map(parseServer(_))
    }

    DeploymentService(state(<<![YamlObject]("state")), breed, environmentVariables(), scale, routing, servers, dependencies(), dialects)
  }

  private def parseServer(implicit source: YamlObject): DeploymentServer =
    DeploymentServer(name, <<![String]("host"), portMapping(), <<![Boolean]("deployed"))

  private def portMapping(name: String = "ports")(implicit source: YamlObject): Map[Int, Int] = {
    <<?[YamlObject](name) match {
      case None ⇒ Map()
      case Some(map: YamlObject) ⇒ map.flatMap {
        case (key, value: Int)    ⇒ (key.toInt -> value) :: Nil
        case (key, value: BigInt) ⇒ (key.toInt -> value.toInt) :: Nil
        case (key, value: String) ⇒ (key.toInt -> value.toInt) :: Nil
        case _                    ⇒ Nil
      } toMap
    }
  }

  private def dependencies(name: String = "dependencies")(implicit source: YamlObject): Map[String, String] = {
    <<?[YamlObject](name) match {
      case None ⇒ Map()
      case Some(map: YamlObject) ⇒ map.flatMap {
        case (key, value: String) ⇒ (key -> value) :: Nil
        case _                    ⇒ Nil
      } toMap
    }
  }

  private def state(implicit source: YamlObject): DeploymentService.State = {
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
