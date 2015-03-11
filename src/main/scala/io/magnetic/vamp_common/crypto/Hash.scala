package io.magnetic.vamp_common.crypto

import java.math.BigInteger
import java.security.MessageDigest


object Hash {

  def hexSha1(content: String, salt: String = "0000"): String = hexHash("SHA1", content, salt)

  private def hexHash(algorithm: String, content: String, salt: String = "0000"): String = {
    val messageDigest = MessageDigest.getInstance(algorithm)
    messageDigest.update(s"$content$salt".getBytes("UTF-8"))
    val digest = messageDigest.digest()
    new BigInteger(1, digest).toString(16)
  }
}
