package io.vamp.core.container_driver.docker.wrapper.model

case class VolumeFromBinding(container: String, mode: Option[VolumeBinding.Mode] = None) {
  lazy val spec = s"$container${mode.map(m ⇒ ":" + m.value).getOrElse("")}"
}

object VolumeFromBinding {
  val Restricted = """(.*):(.*)""".r

  def parse(str: String) = str match {
    case Restricted(container, mode) ⇒ VolumeFromBinding(container, mode match {
      case "ro" ⇒ Some(VolumeBinding.RO)
      case "rw" ⇒ Some(VolumeBinding.RW)
      case _    ⇒ None
    })
    case container ⇒ VolumeFromBinding(container)
  }
}
