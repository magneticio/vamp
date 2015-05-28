package io.vamp.core.cli.commandline

import io.vamp.core.cli.commands._

trait CommandLineBasics {

  import ConsoleHelper._

  def terminateWithError(msg: String): Unit = {
    println(s"ERROR: ".red.bold + "" +  s"$msg".red)
    sys.exit(1)
  }

  def string2Command(s: String): CliCommand = s match {
    case "blueprints" => ListBlueprintsCommand()
    case "breeds" => ListBreedsCommand()
    case "create-breed" => CreateBreedCommand()
    case "clone-breed" => CloneBreedCommand()
    //case "deploy-blueprint" => DeployBlueprintCommand()   // Not yet implemented, so don't expose
    case "deploy-breed" => DeployBreedCommand()
    case "deployments" => ListDeploymentsCommand()
    case "info" => InfoCommand()
    case "inspect-breed" => InspectBreedCommand()
    case "inspect-blueprint" => InspectBlueprintCommand()
    case "inspect-deployment" => InspectDeploymentCommand()
    case "inspect-escalation" => InspectEscalationCommand()
    case "inspect-filter" => InspectFilterCommand()
    case "inspect-routing" => InspectRoutingCommand()
    case "inspect-scale" => InspectScaleCommand()
    case "inspect-sla" => InspectSlaCommand()
    case "escalations" => ListEscalationsCommand()
    case "filters" => ListFiltersCommand()
    case "routings" => ListRoutingsCommand()
    case "scales" => ListScalesCommand()
    //case "remove-blueprint" => RemoveBlueprintCommand()   // Not yet implemented, so don't expose
    case "remove-breed" => RemoveBreedCommand()
    case "slas" => ListSlasCommand()
    case "help" => HelpCommand()
    case "--help" => HelpCommand()
    case "version" => VersionCommand()
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
        showGeneralUsage(ListBlueprintsCommand())
        showGeneralUsage(ListBreedsCommand())
        showGeneralUsage(CloneBreedCommand())
        //showGeneralUsage(DeployBlueprint())    // Not yet implemented, so don't expose
        showGeneralUsage(CreateBreedCommand())
        showGeneralUsage(ListEscalationsCommand())
        showGeneralUsage(ListFiltersCommand())
        showGeneralUsage(ListDeploymentsCommand())
        showGeneralUsage(HelpCommand())
        showGeneralUsage(InfoCommand())
        showGeneralUsage(InspectBreedCommand())
        showGeneralUsage(InspectBlueprintCommand())
        showGeneralUsage(InspectDeploymentCommand())
        showGeneralUsage(InspectEscalationCommand())
        showGeneralUsage(InspectFilterCommand())
        showGeneralUsage(InspectRoutingCommand())
        showGeneralUsage(InspectScaleCommand())
        showGeneralUsage(InspectSlaCommand())
        //showGeneralUsage(RemoveBlueprint())    //Not yet implemented, so don't expose
        showGeneralUsage(RemoveBreedCommand())
        showGeneralUsage(ListRoutingsCommand())
        showGeneralUsage(ListScalesCommand())
        showGeneralUsage(ListSlasCommand())
        showGeneralUsage(VersionCommand())
        println("".reset)
        println(s"Run "+s"$appName COMMMAND --help".bold +  "" + "  for additional help about the different command options")
      }

      case _ => {
        println(s"Usage: ".bold + "" +s"$appName ${command.name} ${if (command.requiresName) "NAME " else ""}${if (command.additionalParams.nonEmpty) command.additionalParams else ""} ")
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

