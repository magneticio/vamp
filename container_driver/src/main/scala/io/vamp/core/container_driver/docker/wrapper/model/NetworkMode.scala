package io.vamp.core.container_driver.docker.wrapper.model

sealed trait NetworkMode {
  def value = getClass.getSimpleName.toLowerCase.replace( """$""", "")
}

object NetworkMode {
  private[this] val ContainerSpec = """container:(.+)""".r

  def unapply(str: String) = str match {
    case "bridge" => Some(NetworkMode.Bridge)
    case "none" => Some(NetworkMode.None)
    case "host" => Some(NetworkMode.Host)
    case ContainerSpec(id) => Some(Container(id))
    case _ => scala.None
  }

  case class Container(id: String) extends NetworkMode {
    override def value = s"${super.value}:$id"
  }

  case object Bridge extends NetworkMode

  case object None extends NetworkMode

  case object Host extends NetworkMode
}
