package io.vamp.core.cli

import io.vamp.core.cli.commandline.Parameters
import io.vamp.core.cli.commands.{HelpCommand, PerformCommand}

trait VampCli extends Parameters {

  def main(args: Array[String]) {
    if (args.length == 0) {
      showHelp(HelpCommand())
      sys.exit(0)
    }

    val cmd = string2Command(args.head)
    if (cmd.requiresSubCommand && args.tail.isEmpty) {
      terminateWithError("Missing artifact type")
    }


    val subCmd = {
      if (cmd.requiresSubCommand) {
          Some(args.tail.head)
      }
      else {
        None
      }
    }

    val argsToProcess = if (cmd.requiresSubCommand) args.tail.tail else args.tail

    implicit val options = readParameters(argsToProcess)

    if (options.contains(help)) showHelp(cmd)

    PerformCommand.doCommand(cmd, subCmd)(options = if (cmd.requiresSubCommand) options else options)
    sys.exit(0)
  }


}
