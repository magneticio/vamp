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
  val commandType : CommandType = CommandType.Other
}

case class BlueprintsCommand() extends CliCommand {
  override val name = "blueprints"
  override val description = "List of blueprints"
  override val usage = "Shows a list of blueprints"
  override val commandType = CommandType.List
}

case class BreedsCommand() extends CliCommand {
  override val name = "breeds"
  override val description = "List of breeds"
  override val usage = "Shows a list of breeds"
  override val commandType = CommandType.List
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
  override val commandType = CommandType.Create
}

case class CreateBreedCommand() extends CliCommand {
  override val name = "create-breed"
  override val additionalParams = "[--file]"
  override val usage = "Create a breed read from the specified filename. When no file name is supplied, stdin will be read."
  override val description = "Create a breed"
  override val parameters = """
                              |  --file               Name of the yaml file [Optional]
                            """.stripMargin
  override val requiresName = true
  override val commandType = CommandType.Create
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
  override val additionalParams = "--deployment --cluster"
  override val usage = "Deploys a breed into an existing deployment cluster"
  override val description = "Deploy a breed into an existing deployment cluster"
  override val parameters = """
                              |  --deployment         Name of the existing deployment
                              |  --cluster            Name of the cluster within the deployment
                            """.stripMargin
  override val requiresName = true
  override val commandType = CommandType.Deploy
}

case class DeploymentsCommand() extends CliCommand {
  override val name = "deployments"
  override val description = "List of deployments"
  override val usage = "Shows a list of deployments"
  override val commandType = CommandType.List
}

case class HelpCommand() extends CliCommand {
  override val name = "help"
  override val description = "This message"
  override val usage = "Displays help message"
}

case class InfoCommand() extends CliCommand {
  override val name = "info"
  override val description = "Information from Vamp Core"
  override val usage = "Returns a JSON blob with information from Vamp Core"
}

case class InspectBreedCommand() extends CliCommand {
  override val name = "inspect-breed"
  override val usage = "Representation of a stored breed"
  override val description = "Return details of the specified breed"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class InspectBlueprintCommand() extends CliCommand {
  override val name = "inspect-blueprint"
  override val usage = "Displays a stored blueprint"
  override val description = "Return details of the specified blueprint"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class InspectDeploymentCommand() extends CliCommand {
  override val name = "inspect-deployment"
  override val usage = "Displays an active deployment"
  override val description = "Return details of the specified deployment"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class InspectEscalationCommand() extends CliCommand {
  override val name = "inspect-escalation"
  override val usage = "Displays a stored escalation"
  override val description = "Return details of the specified escalation"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class InspectFilterCommand() extends CliCommand {
  override val name = "inspect-filter"
  override val usage = "Displays a stored filter"
  override val description = "Return details of the specified filter"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class InspectRoutingCommand() extends CliCommand {
  override val name = "inspect-routing"
  override val usage = "Displays a stored routing"
  override val description = "Return details of the specified routing"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class InspectScaleCommand() extends CliCommand {
  override val name = "inspect-scale"
  override val usage = "Displays a stored scale"
  override val description = "Return details of the specified scale"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class InspectSlaCommand() extends CliCommand {
  override val name = "inspect-sla"
  override val usage = "Displays a stored sla"
  override val description = "Return details of the specified sla"
  override val requiresName = true
  override val commandType = CommandType.Inspect
}

case class RemoveBlueprintCommand() extends CliCommand {
  override val name = "remove-blueprint"
  override val usage = "Removes a blueprint"
  override val description = "Removes a blueprint"
  override val requiresName = true
  override val commandType = CommandType.Delete
}

case class RemoveBreedCommand() extends CliCommand {
  override val name = "remove-breed"
  override val usage = "Removes a breed"
  override val description = "Removes a breed"
  override val requiresName = true
  override val commandType = CommandType.Delete
}

case class SlasCommand() extends CliCommand {
  override val name = "slas"
  override val description = "List of slas"
  override val usage = "Shows a list of slas"
  override val commandType = CommandType.List
}

case class UnknownCommand(override val name: String) extends CliCommand

case class VersionCommand() extends CliCommand {
  override val name = "version"
  override val description = "Show version of the VAMP CLI client"
  override val usage = "Displays the version of the VAMP CLI client"
}
