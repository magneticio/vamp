package io.vamp.common.util

import java.math.BigInteger
import java.security.MessageDigest

object HashUtil {

  def hexSha1(content: String, salt: String = "0000"): String = hexHash("SHA1", content, salt)

  @inline
  def hex(content: String): String = hex(content.getBytes("UTF-8"))

  @inline
  def hex(content: Array[Byte]): String = new BigInteger(1, content).toString(16)

  def hexHash(algorithm: String, content: String, salt: String): String = {
    val messageDigest = MessageDigest.getInstance(algorithm)
    messageDigest.update(s"$content$salt".getBytes("UTF-8"))
    hex(messageDigest.digest())
  }
}
