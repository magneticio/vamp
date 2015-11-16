package io.vamp.core.cli.commandline

object ConsoleHelper {

  implicit class ConsoleHappyPrint(val str: String) extends AnyVal {

    import Console._

    // Colors

    def black = BLACK + str + RESET

    def red = RED + str + RESET

    def green = GREEN + str + RESET

    def yellow = YELLOW + str + RESET

    def blue = BLUE + str + RESET

    def magenta = MAGENTA + str + RESET

    def cyan = CYAN + str + RESET

    def white = WHITE + str + RESET

    def blackBg = BLACK + str + RESET

    def redBg = RED_B + str + RESET

    def greenBg = GREEN_B + str + RESET

    def yellowBg = YELLOW_B + str + RESET

    def blueBg = BLUE_B + str + RESET

    def magentaBg = MAGENTA_B + str + RESET

    def cyanBg = CYAN_B + str + RESET

    def whiteBg = WHITE_B + str + RESET

    // Layout

    def blink = BLINK + str + RESET

    def bold = BOLD + str + RESET

    def underlined = UNDERLINED + str + RESET

    def reset = RESET + str
  }

}