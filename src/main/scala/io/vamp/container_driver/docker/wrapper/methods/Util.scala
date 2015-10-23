package io.vamp.container_driver.docker.wrapper.methods

import dispatch.Req
import io.vamp.container_driver.docker.wrapper.Requests

trait Util {
  self: Requests ⇒

  protected def addContentType(r: Req) = r.setContentType("application/json", "UTF-8")

}
