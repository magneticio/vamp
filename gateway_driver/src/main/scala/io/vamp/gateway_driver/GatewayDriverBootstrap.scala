package io.vamp.gateway_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.common.spi.ClassProvider

import scala.language.postfixOps

class GatewayDriverBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val marshallers: Map[String, GatewayMarshaller] = Config.list("vamp.gateway-driver.marshallers")().collect {
      case config: Map[_, _] ⇒
        val name = config.asInstanceOf[Map[String, String]]("name").trim
        val clazz = config.asInstanceOf[Map[String, String]].get("type").flatMap(ClassProvider.find[GatewayMarshaller]).get
        val template = config.asInstanceOf[Map[String, Map[String, AnyRef]]].getOrElse("template", Map())
        val keyValue = template.get("key-value").forall(_.asInstanceOf[Boolean])
        val file = template.get("file").map(_.asInstanceOf[String].trim).getOrElse("")
        val resource = template.get("resource").map(_.asInstanceOf[String].trim).getOrElse("")
        val parameters = config.asInstanceOf[Map[String, Map[String, AnyRef]]]("parameters")
        val constructor = clazz.getConstructor(classOf[Boolean], classOf[String], classOf[String], classOf[Map[_, _]])
        name → constructor.newInstance(keyValue.asInstanceOf[AnyRef], file, resource, parameters)
    } toMap

    IoC.createActor[GatewayDriverActor](marshallers) :: Nil
  }
}
