package io.vamp.container_driver.docker.wrapper.methods

import io.vamp.container_driver.docker.wrapper.Requests
import io.vamp.container_driver.docker.wrapper.model.Info

trait Generic extends Util {
  self: Requests ⇒

  def info = complete[Info](host / "info")
}
