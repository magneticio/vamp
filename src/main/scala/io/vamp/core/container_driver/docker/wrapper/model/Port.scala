package io.vamp.core.container_driver.docker.wrapper.model

sealed trait Port {
  def value: Int

  def kind: String = getClass.getSimpleName.toLowerCase

  def spec = s"$value/$kind"
}

object Port {
  private[this] val Spec = """(\d+)/(.+)""".r

  case class Tcp(value: Int) extends Port

  case class Udp(value: Int) extends Port

  def unapply(spec: String): Option[Port] = spec match {
    case Spec(value, "tcp") => Some(Tcp(value.toInt))
    case Spec(value, "udp") => Some(Udp(value.toInt))
    case _ => None
  }
}
