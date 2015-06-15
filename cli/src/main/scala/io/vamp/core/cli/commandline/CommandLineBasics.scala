package io.vamp.core.cli.commandline

import io.vamp.core.cli.commands._

trait CommandLineBasics {

  import ConsoleHelper._

  def terminateWithError(msg: String): Unit = {
    println(s"ERROR: ".red.bold + "" +  s"$msg".red)
    sys.exit(1)
  }

  def string2Command(s: String): CliCommand = s match {

    case "clone-breed" => CloneBreedCommand()
    //case "deploy-blueprint" => DeployBlueprintCommand()   // Not yet implemented, so don't expose
    case "deploy-breed" => DeployBreedCommand()
    case "info" => InfoCommand()
    case "help" => HelpCommand()
    case "--help" => HelpCommand()
    case "version" => VersionCommand()

    case "inspect"  => InspectCommand()
    case "list"  => ListCommand()

    case "create"  => CreateCommand()
    case "remove"  => RemoveCommand()

    case c => UnknownCommand(c)
  }





  val NotImplemented = "-- NOT IMPLEMENTED --"

  val appName = "vamp"

  def showHelp(command: CliCommand): Unit = {
    command match {
      case _: HelpCommand => {
        println(s"Usage: ".bold + ""+s"$appName COMMAND [args..]")
        println("")
        println("Commands:")
        showGeneralUsage(CloneBreedCommand())
        //showGeneralUsage(DeployBlueprint())    // Not yet implemented, so don't expose
        showGeneralUsage(CreateCommand())
        showGeneralUsage(DeployBreedCommand())
        showGeneralUsage(HelpCommand())
        showGeneralUsage(InfoCommand())
        showGeneralUsage(InspectCommand())
        showGeneralUsage(ListCommand())
        showGeneralUsage(RemoveCommand())
        showGeneralUsage(VersionCommand())
        println("".reset)
        println(s"Run "+s"$appName COMMMAND --help".bold +  "" + "  for additional help about the different command options")
      }

      case _ => {
        if (command.allowedSubCommands.isEmpty) {
          println(s"Usage: ".bold + "" +s"$appName ${command.name} ${if (command.requiresName) "NAME " else ""}${if (command.additionalParams.nonEmpty) command.additionalParams else ""} ")
        } else {
          println(s"Usage: ".bold + "" +s"$appName ${command.name} ${command.allowedSubCommands.mkString("|")} ${if (command.requiresName) "NAME " else ""}${if (command.additionalParams.nonEmpty) command.additionalParams else ""} ")
        }

      if (command.usage.nonEmpty) {
          println("")
          println(command.usage)
        }
        if (command.parameters.nonEmpty) {
          println(command.parameters)
        }
      }
    }
    sys.exit(0)
  }

  private def showGeneralUsage(command: CliCommand): Unit = {
    println(s"  ${command.name.padTo(20, ' ')}".bold + "" + s"${command.description}".yellow +"")
  }


}

