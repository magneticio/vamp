package io.vamp.core.container_driver.docker.wrapper.methods

import io.vamp.core.container_driver.docker.wrapper.model.AuthConfig
import io.vamp.core.container_driver.docker.wrapper.{ Docker, Requests }

trait Auth extends Util {
  self: Requests ⇒

  def auth(user: String, password: String, email: String): Auth = auth(AuthConfig(user, password, email))

  def auth(cfg: AuthConfig): Auth = Auth(cfg)

  case class Auth(authConfig: AuthConfig) extends Docker.Completion[Unit] {

    def apply[T](handler: Docker.Handler[T]) =
      request(addContentType(host / "auth") << authConfig.json())(handler)
  }
}
