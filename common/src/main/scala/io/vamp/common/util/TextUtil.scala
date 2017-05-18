package io.vamp.common.util

import java.nio.charset.StandardCharsets
import java.util.Base64

object TextUtil {

  /**
   * Converts given string to lower camel case format, e.g. "lower-camel' -> "lowerCamel".
   * Hyphens and underscores are used as delimiters and will be removed from the result.
   * Each substring a part of the first one is only capitalized - no other case conversions are applied.
   *
   * @param s String
   * @return String
   */
  def toLowerCamelCase(s: String): String = {
    val chars = s.replace('-', '_').split('_')
    chars.tail.foldLeft(chars.head)((s1, s2) ⇒ s1 + s2.capitalize)
  }

  /**
   * Converts given string to upper camel case format, e.g. "upper_camel' -> "UpperCamel".
   * Hyphens and underscores are used as delimiters and will be removed from the result.
   * Each substring is only capitalized - no other case conversions are applied.
   *
   * @param s String
   * @return String
   */
  def toUpperCamelCase(s: String): String = toLowerCamelCase(s).capitalize

  /**
   * Converts given string to snake case format, e.g. "UpperCamel' -> "upper_camel".
   *
   * @param s String
   * @return String
   */
  def toSnakeCase(s: String, dash: Boolean = true): String = {
    var lower = false
    val snake = new StringBuilder

    for (c ← s.toCharArray) {
      val previous = lower
      lower = !Character.isUpperCase(c)
      if (previous && !lower)
        if (dash) snake.append("-") else snake.append("_")
      snake.append(c)
    }

    snake.toString().toLowerCase
  }

  def encodeBase64(data: String): String = Base64.getEncoder.encodeToString(data.getBytes(StandardCharsets.UTF_8))
}
