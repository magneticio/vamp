package io.vamp.core.cli.commands

import io.vamp.core.cli.commands.CommandType.CommandType

object CommandType extends Enumeration {
  type CommandType = Value
  val Inspect, List, Create, Delete, Update, Deploy, Other = Value
}

trait CliCommand {
  val name = "Add Name"
  val usage = "Add usage description"
  val additionalParams = ""
  val description = ""
  val parameters = ""
  val requiresName: Boolean = false
  val commandType: CommandType = CommandType.Other
  val requiresHostConnection : Boolean = true
  val allowedSubCommands :List[String]= List.empty
  def jsonOutput = "  --json               Output Json instead of Yaml[Optional]"
  val allArtifacts = List("blueprint","breed","deployment","escalation", "filter", "routing", "scale", "sla")
  val allArtifactsPlural = List("blueprints","breeds","deployments","escalations", "filters", "routings", "scales", "slas")
}

case class ListCommand() extends CliCommand {
  override val name = "list"
  override val description = "Shows a list of artifacts"
  override val usage = "Shows a list of artifacts"
  override val commandType = CommandType.List
  override val allowedSubCommands = allArtifactsPlural
}

case class InspectCommand() extends CliCommand {
  override val name = "inspect"
  override val description = "Shows the details of the specified artifact"
  override val usage =
    """Shows the details of the specified artifact.
    """.stripMargin
  override val additionalParams = "--json"
  override val parameters = jsonOutput
  override val commandType = CommandType.Inspect
  override val requiresName = true
  override val allowedSubCommands = allArtifacts
}

case class CloneBreedCommand() extends CliCommand {
  override val name = "clone-breed"
  override val additionalParams = "--destination [--deployable]"
  override val usage = "Clones an existing breed"
  override val description = "Clone a breed"
  override val parameters = """
                              |  --destination        Name of the new breed
                              |  --deployable         Name of the deployable [Optional]
                            """.stripMargin
  override val requiresName = true
  override val commandType = CommandType.Other
}

case class CreateCommand() extends CliCommand {
  override val name = "create"
  override val additionalParams = "[--file]"
  override val usage = "Create an artifact read from the specified filename. When no file name is supplied, stdin will be read."
  override val description = "Create an artifact"
  override val parameters = """
                              |  --file               Name of the yaml file [Optional]
                            """.stripMargin
  override val requiresName = true
  override val commandType = CommandType.Create
  override val allowedSubCommands = List("breed")
}

case class DeployBlueprintCommand() extends CliCommand {
  override val name = "deploy-blueprint"
  override val usage = "Deploys a blueprint"
  override val description = "Deploys a blueprint"
  override val requiresName = true
  override val commandType = CommandType.Deploy
}

case class DeployBreedCommand() extends CliCommand {
  override val name = "deploy-breed"
  override val additionalParams = "--deployment --cluster --routing --scale"
  override val usage = "Deploys a breed into an existing deployment cluster"
  override val description = "Deploy a breed into an existing deployment cluster"
  override val parameters = """
                              |  --deployment         Name of the existing deployment
                              |  --cluster            Name of the cluster within the deployment
                              |  --routing            Name of the routing to apply [Optional]
                              |  --scale              Name of the scale to apply [Optional]
                            """.stripMargin
  override val requiresName = true
  override val commandType = CommandType.Deploy
}

case class HelpCommand() extends CliCommand {
  override val name = "help"
  override val description = "This message"
  override val usage = "Displays help message"
  override val requiresHostConnection = false
}

case class InfoCommand() extends CliCommand {
  override val name = "info"
  override val description = "Information from Vamp Core"
  override val usage = "Returns a JSON blob with information from Vamp Core"
}


case class RemoveCommand() extends CliCommand {
  override val name = "remove"
  override val usage = "Removes artifact"
  override val description = "Removes an artifact"
  override val requiresName = true
  override val commandType = CommandType.Delete
  override val allowedSubCommands= List("breed")
}

case class UnknownCommand(override val name: String) extends CliCommand

case class VersionCommand() extends CliCommand {
  override val name = "version"
  override val description = "Show version of the VAMP CLI client"
  override val usage = "Displays the version of the VAMP CLI client"
  override val requiresHostConnection = false
}
