package io.vamp.core.cli.commands

import io.vamp.core.cli.commands.CommandType.CommandType

object CommandType extends Enumeration {
  type CommandType = Value
  val Inspect, List, Create, Delete, Generate, Update, Deploy, Merge, Other = Value
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

case class CreateCommand() extends CliCommand {
  override val name = "create"
  override val additionalParams = "[--file]"
  override val usage = "Create an artifact read from the specified filename. When no file name is supplied, stdin will be read."
  override val description = "Create an artifact"
  override val parameters = """  --file               Name of the yaml file [Optional]
                              |  --stdin              Read file from stdin [Optional]
                            """.stripMargin
  override val requiresName  = false
  override val commandType = CommandType.Create
  override val allowedSubCommands = allArtifacts.filter(_ != "deployment")
}

case class DeployCommand() extends CliCommand {
  override val name = "deploy"
  override val additionalParams = "[NAME] [--file|--stdin] [--deployment]"
  override val usage = "Deploys a blueprint specified by NAME, read from the specified filename or read from stdin."
  override val description = "Deploys a blueprint"
  override val parameters = """  --file               Name of the yaml file [Optional]
                              |  --stdin              Read file from stdin [Optional]
                              |  --deployment         Name of the deployment to update [Optional]
                            """.stripMargin
  override val commandType = CommandType.Deploy
}

case class GenerateCommand() extends CliCommand {
  override val name = "generate"
  override val additionalParams = "[--file|--stdin]"
  override val usage = "Generates an artifact"
  override val description = "Generates an artifact"
  override val parameters = """  --file               Name of the yaml file to preload the generation [Optional]
                              |  --stdin              Read file from stdin [Optional]
                              |
                              |For 'generate breed':
                              |  --deployable         Deployable specification [Optional]
                              |
                              |For 'generate blueprint':
                              |  --cluster            Name of the cluster
                              |  --breed              Name of the breed   [Optional, requires --cluster]
                              |  --routing            Name of the routing [Optional, requires --breed]
                              |  --scale              Name of the scale   [Optional, requires --breed]
                            """.stripMargin
  override val requiresName = false
  override val commandType = CommandType.Generate
  override val allowedSubCommands = List("breed","blueprint", "filter", "routing", "scale")
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


case class MergeCommand() extends CliCommand {
  override val name = "merge"
  override val additionalParams = "--deployment|--blueprint [--file|--stdin]"
  override val usage =
    """Merges a blueprint with an existing deployment or blueprint.
      |Either specify a deployment or blueprint in which the blueprint should be merged
      |The blueprint can be specified by NAME, read from the specified filename or read from stdin.
      """.stripMargin
  override val description = "Merge a blueprint with an existing deployment or blueprint"
  override val parameters = """  --file               Name of the yaml file [Optional]
                            """.stripMargin
  override val commandType = CommandType.Merge
}


case class RemoveCommand() extends CliCommand {
  override val name = "remove"
  override val usage = "Removes artifact"
  override val description = "Removes an artifact"
  override val requiresName = true
  override val commandType = CommandType.Delete
  override val allowedSubCommands= allArtifacts.filter(_ != "deployment")
}

case class UnknownCommand(override val name: String) extends CliCommand

case class VersionCommand() extends CliCommand {
  override val name = "version"
  override val description = "Show version of the VAMP CLI client"
  override val usage = "Displays the version of the VAMP CLI client"
  override val requiresHostConnection = false
}
