package io.vamp.common.akka

import akka.actor.Props
import io.vamp.common.text.Text

trait ActorDescription {
  def props(args: Any*): Props

  def name: String = Text.toSnakeCase(getClass.getSimpleName)
}
