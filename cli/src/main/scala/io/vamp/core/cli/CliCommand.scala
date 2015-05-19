package io.vamp.core.cli


trait CliCommand {

  val name = "Add Name"
  val usage = "Add usage description"
  val additionalParams = ""
  val description = ""
  val parameters = ""
  val requiresName: Boolean = false
}

case class BlueprintsCommand() extends CliCommand {
  override val name = "blueprint"
  override val description = "List of blueprints"
  override val usage = "Shows a list of blueprints"
}

case class BreedsCommand() extends CliCommand {
  override val name = "breeds"
  override val description = "List of breeds"
  override val usage = "Shows a list of breeds"
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
}

case class DeployBlueprintCommand() extends CliCommand {
  override val name = "deploy-blueprint"
  override val usage = "Deploys a blueprint"
  override val description = "Deploys a blueprint"
  override val requiresName = true
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
}

case class DeploymentsCommand() extends CliCommand {
  override val name = "deployments"
  override val description = "List of deployments"
  override val usage = "Shows a list of deployments"
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
  override val usage = "JSON representation of a stored breed"
  override val description = "Return details of the specified  breed"
  override val requiresName = true
}

case class InspectBlueprintCommand() extends CliCommand {
  override val name = "inspect-blueprint"
  override val usage = "JSON representation of a stored blueprint"
  override val description = "Return details of the specified blueprint"
  override val requiresName = true
}

case class InspectDeploymentCommand() extends CliCommand {
  override val name = "inspect-deployment"
  override val usage = "JSON representation of a active deployment"
  override val description = "Return details of the specified deployment"
  override val requiresName = true
}

case class RemoveBlueprintCommand() extends CliCommand {
  override val name = "remove-blueprint"
  override val usage = "Removes a blueprint"
  override val description = "Removes a blueprint"
  override val requiresName = true
}

case class RemoveBreedCommand() extends CliCommand {
  override val name = "remove-breed"
  override val usage = "Removes a breed"
  override val description = "Removes a breed"
  override val requiresName = true
}

case class SlasCommand() extends CliCommand {
  override val name = "slas"
  override val description = "List of slas"
  override val usage = "Shows a list of slas"
}

case class UnknownCommand(override val name: String) extends CliCommand
