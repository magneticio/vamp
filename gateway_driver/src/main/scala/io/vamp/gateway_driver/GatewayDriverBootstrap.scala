package io.vamp.gateway_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.common.spi.ClassProvider

import scala.io.Source
import scala.language.postfixOps

class GatewayDriverBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val marshallers: Map[String, GatewayMarshallerDefinition] = Config.list("vamp.gateway-driver.marshallers")().collect {
      case config: Map[_, _] ⇒
        val name = config.asInstanceOf[Map[String, String]]("name").trim
        val clazz = config.asInstanceOf[Map[String, String]].get("type").flatMap(ClassProvider.find[GatewayMarshaller]).get

        logger.info(s"Gateway marshaller: ${config.asInstanceOf[Map[String, String]].getOrElse("type", "")}")

        val template = config.asInstanceOf[Map[String, Map[String, AnyRef]]].getOrElse("template", Map())
        val file = template.get("file").map(_.asInstanceOf[String].trim).getOrElse("")
        val resource = template.get("resource").map(_.asInstanceOf[String].trim).getOrElse("")

        name → GatewayMarshallerDefinition(
          clazz.newInstance,
          if (file.nonEmpty) Source.fromFile(file).mkString else if (resource.nonEmpty) Source.fromURL(getClass.getResource(resource)).mkString else ""
        )
    } toMap

    IoC.createActor[GatewayDriverActor](marshallers) :: Nil
  }
}
