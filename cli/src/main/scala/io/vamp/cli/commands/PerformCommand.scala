package io.vamp.cli.commands

import io.vamp.cli.backend.VampHostCalls
import io.vamp.cli.commandline.ConsoleHelper
import io.vamp.model.artifact._
import io.vamp.model.reader._

object PerformCommand extends Generate {

  import ConsoleHelper._

  def doCommand(command: CliCommand, subCommand: Option[String])(implicit options: OptionMap): Unit = {

    implicit val vampHost: String = if (command.requiresHostConnection) getVampHost.getOrElse("Host not specified") else "Not required"

    command.commandType match {
      case CommandType.List     ⇒ doListCommand(subCommand)
      case CommandType.Inspect  ⇒ doInspectCommand(subCommand)
      case CommandType.Create   ⇒ doCreateCommand(subCommand)
      case CommandType.Delete   ⇒ doDeleteCommand(subCommand)
      case CommandType.Generate ⇒ doGenerateCommand(subCommand)
      case CommandType.Deploy   ⇒ doDeployCommand(command)
      case CommandType.Merge    ⇒ doMergeCommand
      case CommandType.Undeploy ⇒ doUndeployCommand
      case CommandType.Update   ⇒ doUpdateCommand(subCommand)
      case CommandType.Other    ⇒ doOtherCommand(command)
    }
  }

  private def doListCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {
    case Some("breeds") ⇒
      println("NAME".padTo(25, ' ').bold.cyan + "DEPLOYABLE".bold.cyan)
      VampHostCalls.getBreeds.foreach({ case b: DefaultBreed ⇒ println(s"${b.name.padTo(25, ' ')}${b.deployable.name}") })

    case Some("blueprints") ⇒
      println("NAME".padTo(40, ' ').bold.cyan + "GATEWAYS".bold.cyan)
      VampHostCalls.getBlueprints.foreach({ case blueprint: DefaultBlueprint ⇒ println(s"${blueprint.name.padTo(40, ' ')}${blueprint.gateways.map(e ⇒ s"${e.name} -> ...").mkString(", ")}") })

    case Some("gateways") ⇒
      println("NAME".padTo(40, ' ').bold.cyan + "PORT".bold.cyan)
      VampHostCalls.getGateways.foreach({ case gateway: Gateway ⇒ println(s"${gateway.name.padTo(40, ' ')}${gateway.port.toValue}") })

    case Some("deployments") ⇒
      println("NAME".padTo(40, ' ').bold.cyan + "CLUSTERS".bold.cyan)
      VampHostCalls.getDeployments.foreach(deployment ⇒ println(s"${deployment.name.padTo(40, ' ')}${deployment.clusters.map(c ⇒ s"${c.name}").mkString(", ")}"))

    case Some("escalations") ⇒
      println("NAME".padTo(25, ' ').bold.cyan + "TYPE".padTo(20, ' ').bold.cyan + "SETTINGS".bold.cyan)
      VampHostCalls.getEscalations.foreach({
        case b: ScaleInstancesEscalation ⇒ println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleCpuEscalation       ⇒ println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleMemoryEscalation    ⇒ println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: Escalation               ⇒ println(s"${b.name.padTo(25, ' ')}")
        case x                           ⇒ println(x)
      })

    case Some("conditions") ⇒
      println("NAME".padTo(25, ' ').bold.cyan + "CONDITION".bold.cyan)
      VampHostCalls.getConditions.foreach({
        case b: DefaultCondition ⇒ println(s"${b.name.padTo(25, ' ')}${b.definition}")
        case _                   ⇒
      })

    case Some("rewrites") ⇒
      println("NAME".padTo(25, ' ').bold.cyan + "CONDITION".bold.cyan)
      VampHostCalls.getRewrites.foreach({
        case b: PathRewrite ⇒ println(s"${b.name.padTo(25, ' ')}${b.definition}")
        case _              ⇒
      })

    case Some("routings") ⇒
      println("NAME".padTo(25, ' ').bold.cyan + "FILTERS".bold.cyan)
      VampHostCalls.getRoutings.foreach({
        case b: DefaultRoute ⇒ println(s"${b.name.padTo(25, ' ')}${
          b.conditions.map({
            case d: DefaultCondition ⇒ s"${d.definition}"
            case _                   ⇒ ""
          }).mkString(", ")
        }")
        case _ ⇒
      })

    case Some("scales") ⇒
      println("NAME".padTo(25, ' ').bold.cyan + "CPU".padTo(7, ' ').bold.cyan + "MEMORY".padTo(10, ' ').bold.cyan + "INSTANCES".bold.cyan)
      VampHostCalls.getScales.foreach({ case b: DefaultScale ⇒ println(s"${b.name.padTo(25, ' ')}${b.cpu.toString.padTo(7, ' ')}${b.memory.toString.padTo(10, ' ')}${b.instances}") })

    case Some("slas") ⇒
      println("NAME".bold.cyan)
      VampHostCalls.getSlas.foreach(sla ⇒ println(s"${sla.name}"))

    case Some(invalid) ⇒ terminateWithError(s"Artifact type unknown: '$invalid'")
    case None          ⇒ terminateWithError("Please specify an artifact type")

  }

  private def doInspectCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = {
    val artifact: Option[Artifact] = subCommand match {
      case Some("breed")     ⇒ VampHostCalls.getBreed(getParameter(name))
      case Some("blueprint") ⇒ VampHostCalls.getBlueprint(getParameter(name))
      case Some("gateway")   ⇒ VampHostCalls.getGateway(getParameter(name))
      case Some("deployment") ⇒ getOptionalParameter(as_blueprint) match {
        case Some(_) ⇒ VampHostCalls.getDeploymentAsBlueprint(getParameter(name))
        case None    ⇒ VampHostCalls.getDeployment(getParameter(name))
      }
      case Some("escalation") ⇒ VampHostCalls.getEscalation(getParameter(name))
      case Some("filter")     ⇒ VampHostCalls.getCondition(getParameter(name))
      case Some("rewrite")    ⇒ VampHostCalls.getRewrite(getParameter(name))
      case Some("route")      ⇒ VampHostCalls.getRoute(getParameter(name))
      case Some("scale")      ⇒ VampHostCalls.getScale(getParameter(name))
      case Some("sla")        ⇒ VampHostCalls.getSla(getParameter(name))
      case Some(invalid)      ⇒ terminateWithError(s"Artifact type unknown: '$invalid'")
      case None               ⇒ terminateWithError("Please specify an artifact type")
    }
    printArtifact(artifact)
  }

  private def doCreateCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {
    case Some("breed")      ⇒ printArtifact(VampHostCalls.createBreed(readFileContent))
    case Some("blueprint")  ⇒ printArtifact(VampHostCalls.createBlueprint(readFileContent))
    case Some("gateway")    ⇒ printArtifact(VampHostCalls.createGateway(readFileContent))
    case Some("escalation") ⇒ printArtifact(VampHostCalls.createEscalation(readFileContent))
    case Some("filter")     ⇒ printArtifact(VampHostCalls.createCondition(readFileContent))
    case Some("rewrite")    ⇒ printArtifact(VampHostCalls.createRewrite(readFileContent))
    case Some("routes")     ⇒ printArtifact(VampHostCalls.createRoute(readFileContent))
    case Some("scale")      ⇒ printArtifact(VampHostCalls.createScale(readFileContent))
    case Some("sla")        ⇒ printArtifact(VampHostCalls.createSla(readFileContent))
    case Some(invalid)      ⇒ terminateWithError(s"Unsupported artifact '$invalid'")
    case None               ⇒ terminateWithError("Please specify an artifact type")
  }

  private def doUpdateCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {
    case Some("breed")      ⇒ printArtifact(VampHostCalls.updateBreed(getParameter(name), readFileContent))
    case Some("blueprint")  ⇒ printArtifact(VampHostCalls.updateBlueprint(getParameter(name), readFileContent))
    case Some("gateway")    ⇒ printArtifact(VampHostCalls.updateGateway(getParameter(name), readFileContent))
    case Some("escalation") ⇒ printArtifact(VampHostCalls.updateEscalation(getParameter(name), readFileContent))
    case Some("filter")     ⇒ printArtifact(VampHostCalls.updateCondition(getParameter(name), readFileContent))
    case Some("rewrite")    ⇒ printArtifact(VampHostCalls.updateRewrite(getParameter(name), readFileContent))
    case Some("routes")     ⇒ printArtifact(VampHostCalls.updateRoute(getParameter(name), readFileContent))
    case Some("scale")      ⇒ printArtifact(VampHostCalls.updateScale(getParameter(name), readFileContent))
    case Some("sla")        ⇒ printArtifact(VampHostCalls.updateSla(getParameter(name), readFileContent))
    case Some(invalid)      ⇒ terminateWithError(s"Artifact type unknown: '$invalid'")
    case None               ⇒ terminateWithError("Please specify an artifact type")
  }

  private def doDeleteCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {
    case Some("breed")      ⇒ VampHostCalls.deleteBreed(getParameter(name))
    case Some("blueprint")  ⇒ VampHostCalls.deleteBlueprint(getParameter(name))
    case Some("gateways")   ⇒ VampHostCalls.deleteGateway(getParameter(name))
    case Some("escalation") ⇒ VampHostCalls.deleteEscalation(getParameter(name))
    case Some("filter")     ⇒ VampHostCalls.deleteCondition(getParameter(name))
    case Some("rewrite")    ⇒ VampHostCalls.deleteRewrite(getParameter(name))
    case Some("routes")     ⇒ VampHostCalls.deleteRoute(getParameter(name))
    case Some("scale")      ⇒ VampHostCalls.deleteScale(getParameter(name))
    case Some("sla")        ⇒ VampHostCalls.deleteSla(getParameter(name))
    case Some(invalid)      ⇒ terminateWithError(s"Unsupported artifact '$invalid'")
    case None               ⇒ terminateWithError(s"Artifact & name are required")
  }

  private def doDeployCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _: DeployCommand ⇒
      getBlueprint() match {
        case Some(blueprintToDeploy: DefaultBlueprint) ⇒
          getOptionalParameter(deployment) match {
            //Existing deployment
            case Some(nameOfDeployment) ⇒
              VampHostCalls.getDeploymentAsBlueprint(nameOfDeployment) match {
                case Some(deployedBlueprint: DefaultBlueprint) ⇒
                  printArtifact(VampHostCalls.updateDeployment(nameOfDeployment, artifactToYaml(blueprintToDeploy)))
                case _ ⇒ terminateWithError(s"Deployment not found")
              }
            // New deployment
            case None ⇒
              printArtifact(VampHostCalls.deploy(artifactToYaml(blueprintToDeploy)))
          }
        case _ ⇒ terminateWithError("No usable blueprint found.")
      }

    case _ ⇒ unhandledCommand _
  }

  private def doUndeployCommand(implicit vampHost: String, options: OptionMap) = {
    val stuffToRemove: Option[String] = getOptionalParameter(blueprint) match {
      case Some(nameOfBlueprint) ⇒ VampHostCalls.getBlueprint(nameOfBlueprint) match {
        case Some(storedBlueprint) ⇒ Some(artifactToYaml(storedBlueprint))
        case None ⇒
          terminateWithError(s"Blueprint $nameOfBlueprint not found")
          None
      }
      case None ⇒
        readOptionalFileContent match {
          case Some(fileContent) ⇒ Some(fileContent) // get supplied blueprint from file or stdin
          case None ⇒ VampHostCalls.getDeployment(name) match {
            case Some(existingDeployment) ⇒ Some(artifactToYaml(existingDeployment)) // get running deployment
            case None ⇒
              terminateWithError("Deployment not found")
              None
          }
        }
    }
    println(VampHostCalls.undeploy(getParameter(name), stuffToRemove).getOrElse(""))
  }

  private def getBlueprint()(implicit vampHost: String, options: OptionMap): Option[Blueprint] = getOptionalParameter(name) match {
    case Some(nameOfBlueprint) ⇒ VampHostCalls.getBlueprint(nameOfBlueprint)
    case None                  ⇒ Some(BlueprintReader.read(readFileContent))
  }

  private def mergeBlueprints(sourceBlueprint: DefaultBlueprint, additionalBlueprint: DefaultBlueprint): DefaultBlueprint = sourceBlueprint.copy(
    clusters = sourceBlueprint.clusters ++ additionalBlueprint.clusters,
    gateways = sourceBlueprint.gateways ++ additionalBlueprint.gateways,
    environmentVariables = sourceBlueprint.environmentVariables ++ additionalBlueprint.environmentVariables
  )

  private def doOtherCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {
    case _: InfoCommand    ⇒ println(VampHostCalls.info.getOrElse(""))
    case _: HelpCommand    ⇒ showHelp(HelpCommand())
    case _: VersionCommand ⇒ println(s"CLI version: " + s"${getClass.getPackage.getImplementationVersion}".yellow.bold)
    case x: UnknownCommand ⇒ terminateWithError(s"Unknown command '${x.name}'")
    case _                 ⇒ unhandledCommand _
  }

  private def doMergeCommand(implicit vampHost: String, options: OptionMap) = getOptionalParameter(deployment) match {
    case Some(deploymentId) ⇒
      VampHostCalls.getDeploymentAsBlueprint(deploymentId) match {
        case Some(deployedBlueprint: DefaultBlueprint) ⇒
          getBlueprint() match {
            case Some(bp: DefaultBlueprint) ⇒ printArtifact(Some(mergeBlueprints(deployedBlueprint, bp)))
            case _                          ⇒ terminateWithError("No usable blueprint found.")
          }
        case _ ⇒ terminateWithError(s"Deployment not found")
      }
    case None ⇒ getOptionalParameter(name) match {
      case Some(blueprintName) ⇒
        VampHostCalls.getBlueprint(blueprintName) match {
          case Some(existingBlueprint: DefaultBlueprint) ⇒
            getBlueprint() match {
              case Some(bp: DefaultBlueprint) ⇒ printArtifact(Some(mergeBlueprints(existingBlueprint, bp)))
              case _                          ⇒ terminateWithError("No usable blueprint found.")
            }
          case _ ⇒ terminateWithError(s"Blueprint not found")
        }
      case None ⇒ terminateWithError("No blueprint name specified")
    }
    case _ ⇒ terminateWithError(s"No deployment or blueprint specified")
  }

  private def unhandledCommand(command: CliCommand) = terminateWithError(s"Unhandled command '${command.name}'")

  private def getVampHost(implicit options: Map[Symbol, String]): Option[String] =
    getOptionalParameter(host) match {
      case Some(vampHost: String) ⇒ Some(vampHost)
      case _ ⇒
        println("Sorry, I don't know to which Vamp host I should talk.".bold.red)
        println()
        println("Setup a host in the VAMP_HOST environment variable, or use the --host command line argument.")
        println("Please check http://vamp.io/installation for further details")
        sys.exit(1)
    }

}
