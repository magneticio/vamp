package io.vamp.core.container_driver.docker.wrapper.model

import java.nio.charset.Charset

import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.base64.{ Base64 ⇒ Encoder }
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

case class AuthConfig(user: String, password: String, email: String, server: String = "https://index.docker.io/v1/") {

  def json(source: Map[_, _] = parameters) = encode(write(source)(DefaultFormats))

  def parameters = Map("username" -> user, "password" -> password, "email" -> email, "serveraddress" -> server)

  private def encode(str: String): String =
    Encoder.encode(ChannelBuffers.wrappedBuffer(str.getBytes("utf8")), false).toString(Charset.forName("utf8"))
}
