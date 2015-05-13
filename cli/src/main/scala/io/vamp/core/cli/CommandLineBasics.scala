package io.vamp.core.cli


trait CommandLineBasics {

  def terminateWithError(msg: String): Unit = {
    println(s"ERROR: $msg")
    sys.exit(1)
  }

  def string2Command(s: String): CliCommand = s match {
    case "blueprints" => BlueprintsCommand()
    case "breeds" => BreedsCommand()
    case "clone-breed" => CloneBreedCommand()
    //case "deploy-blueprint" => DeployBlueprintCommand()   // Not yet implemented, so don't expose
    case "deploy-breed" => DeployBreed()
    case "inspect-breed" => InspectBreedCommand()
    case "inspect-blueprint" => InspectBlueprintCommand()
    //case "remove-blueprint" => RemoveBlueprintCommand()   // Not yet implemented, so don't expose
    case "remove-breed" => RemoveBreedCommand()
    case "help" => HelpCommand()
    case "--help" => HelpCommand()
    case c => UnknownCommand(c)
  }

  val NotImplemented = "-- NOT IMPLEMENTED --"

  val appName = "vamp-cli"

  def showHelp(command: CliCommand): Unit = {
    command match {
      case _: HelpCommand => {
        println(s"Usage: $appName COMMAND [args..]")
        println("")
        println("Commands:")
        showGeneralUsage(BlueprintsCommand())
        showGeneralUsage(BreedsCommand())
        showGeneralUsage(CloneBreedCommand())
        //showGeneralUsage(DeployBlueprint())    // Not yet implemented, so don't expose
        showGeneralUsage(DeployBreed())
        showGeneralUsage(HelpCommand())
        showGeneralUsage(InspectBreedCommand())
        showGeneralUsage(InspectBlueprintCommand())
        //showGeneralUsage(RemoveBlueprint())    //Not yet implemented, so don't expose
        showGeneralUsage(RemoveBreedCommand())
        println("")
        println(s"Run '$appName COMMMAND --help' for help about the different command options")
      }

      case _ => {
        println(s"Usage: $appName ${command.name} ${if (command.requiresName) "NAME " else ""}${if (command.additionalParams.nonEmpty) command.additionalParams else ""} ")
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
    println(s"  ${command.name.padTo(20, ' ')} ${command.description}")
  }


}
