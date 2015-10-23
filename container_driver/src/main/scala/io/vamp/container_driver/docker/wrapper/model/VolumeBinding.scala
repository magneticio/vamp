package io.vamp.container_driver.docker.wrapper.model

case class VolumeBinding(
    hostPath: String, containerPath: String,
    mode: Option[VolumeBinding.Mode] = None) {
  lazy val spec = s"$hostPath:$containerPath${mode.map(m ⇒ ":" + m.value).getOrElse("")}"
}

object VolumeBinding {
  val Spec = """(.*):(.*)""".r
  val Restricted = """(.*):(.*):(.*)""".r

  trait Mode {
    def value: String = getClass.getSimpleName.stripSuffix("$")
  }

  case object RO extends Mode

  case object RW extends Mode

  def parse(str: String) = str match {
    case Spec(host, container) ⇒
      VolumeBinding(host, container)
    case Restricted(host, container, mode) ⇒
      VolumeBinding(host, container, mode match {
        case "ro" ⇒ Some(RO)
        case "rw" ⇒ Some(RW)
        case _    ⇒ None
      })
  }
}
