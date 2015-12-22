package io.vamp.gateway_driver

import io.vamp.common.crypto.Hash
import io.vamp.gateway_driver.haproxy.Flatten
import io.vamp.model.artifact._

object GatewayMarshaller {

  private val referenceMatcher = """^[a-zA-Z0-9][a-zA-Z0-9.\-_]{3,63}$""".r

  def name(deployment: Deployment, port: Port): String = flatten(path2string(deployment.name :: port.name :: Nil))

  def name(deployment: Deployment, cluster: DeploymentCluster, port: Port): String =
    flatten(path2string(deployment.name :: cluster.name :: port.name :: Nil))

  def name(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, port: Port): String = {
    val name1 = path2string(deployment.name :: cluster.name :: port.name :: Nil)
    val name2 = path2string(deployment.name :: cluster.name :: service.breed.name :: port.name :: Nil)
    flatten(s"$name1/$name2")
  }

  def name(gateway: Gateway, path: GatewayPath = Nil): String = path2string(path) match {
    case ""  ⇒ flatten(path2string(gateway.name))
    case str ⇒ flatten(s"${path2string(gateway.name)}/$str")
  }

  private def path2string(path: GatewayPath): String = {
    path.segments match {
      case Nil                   ⇒ ""
      case some if some.size < 4 ⇒ path2string(some :+ "-")
      case some                  ⇒ some.mkString("/")
    }
  }

  private def flatten(string: String) = {
    val flatten = Flatten.flatten(string)
    flatten match {
      case referenceMatcher(_*) ⇒ flatten
      case _                    ⇒ Hash.hexSha1(flatten)
    }
  }
}

trait GatewayMarshaller {

  def info: AnyRef

  def path: List[String]

  def marshall(gateways: List[Gateway]): String
}
