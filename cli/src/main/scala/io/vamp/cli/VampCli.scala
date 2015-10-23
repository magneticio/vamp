package io.vamp.cli

import io.vamp.cli.commandline.Parameters
import io.vamp.cli.commands.{ HelpCommand, PerformCommand }

trait VampCli extends Parameters {

  def main(args: Array[String]) {
    if (args.length == 0) {
      showHelp(HelpCommand())
      sys.exit(0)
    }

    val cmd = string2Command(args.head)
    if (cmd.requiresArtifact && args.tail.isEmpty) {
      terminateWithError("Missing artifact type")
    }

    val subCmd: Option[String] = {
      if (cmd.requiresArtifact) {
        Some(args.tail.head)
      } else {
        None
      }
    }

    val argsToProcess = if (cmd.requiresArtifact) args.tail.tail else args.tail

    implicit val options = readParameters(argsToProcess)

    if ((cmd.requiresArtifact && subCmd.contains("--help")) || options.contains(help)) showHelp(cmd)

    PerformCommand.doCommand(cmd, subCmd)(options = if (cmd.requiresArtifact) options else options)
    sys.exit(0)
  }

}
