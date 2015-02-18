package io.magnetic.vamp_common.text

object Text {

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
    chars.tail.foldLeft(chars.head)((s1, s2) => s1 + s2.capitalize)
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
}
