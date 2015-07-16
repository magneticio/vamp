package io.vamp.core.cli.commands


import io.vamp.core.cli.backend.VampHostCalls
import io.vamp.core.cli.commandline.{ConsoleHelper, Parameters}
import io.vamp.core.model.artifact._
import io.vamp.core.model.reader._
import io.vamp.core.model.serialization.CoreSerializationFormat
import org.json4s.native.Serialization._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

import scala.io.Source
import scala.language.{implicitConversions, postfixOps}


object PerformCommand extends Parameters {

  import ConsoleHelper._


  implicit val formats = CoreSerializationFormat.default

  def doCommand(command: CliCommand)(implicit options: OptionMap): Unit = {

    implicit val vampHost: String = if (command.requiresHostConnection) getVampHost.getOrElse("Host not specified") else "Not required"

    command.commandType match {
      case CommandType.List => doListCommand
      case CommandType.Inspect => doInspectCommand
      case CommandType.Create => doCreateCommand
      case CommandType.Delete => doDeleteCommand
      case CommandType.Generate => doGenerateCommand
      case CommandType.Deploy => doDeployCommand(command)
      case CommandType.Merge => doMergeCommand
      case CommandType.Undeploy => doUndeployCommand
      case CommandType.Update => doUpdateCommand
      case CommandType.Other => doOtherCommand(command)
    }
  }

  private def doListCommand(implicit vampHost: String, options: OptionMap) = getParameter(name) match {
    case "breeds" =>
      println("NAME".padTo(25, ' ').bold.cyan + "DEPLOYABLE".bold.cyan)
      VampHostCalls.getBreeds.foreach({ case b: DefaultBreed => println(s"${b.name.padTo(25, ' ')}${b.deployable.name}") })

    case "blueprints" =>
      println("NAME".padTo(40, ' ').bold.cyan + "ENDPOINTS".bold.cyan)
      VampHostCalls.getBlueprints.foreach({ case blueprint: DefaultBlueprint => println(s"${blueprint.name.padTo(40, ' ')}${blueprint.endpoints.map(e => s"${e.name} -> ${e.value.get}").mkString(", ")}") })

    case "deployments" =>
      println("NAME".padTo(40, ' ').bold.cyan + "CLUSTERS".bold.cyan)
      VampHostCalls.getDeployments.foreach(deployment => println(s"${deployment.name.padTo(40, ' ')}${deployment.clusters.map(c => s"${c.name}").mkString(", ")}"))

    case "escalations" =>
      println("NAME".padTo(25, ' ').bold.cyan + "TYPE".padTo(20, ' ').bold.cyan + "SETTINGS".bold.cyan)
      VampHostCalls.getEscalations.foreach({
        case b: ScaleInstancesEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleCpuEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleMemoryEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: Escalation => println(s"${b.name.padTo(25, ' ')}")
        case x => println(x)
      })

    case "filters" =>
      println("NAME".padTo(25, ' ').bold.cyan + "CONDITION".bold.cyan)
      VampHostCalls.getFilters.foreach({ case b: DefaultFilter => println(s"${b.name.padTo(25, ' ')}${b.condition}") })

    case "routings" =>
      println("NAME".padTo(25, ' ').bold.cyan + "FILTERS".bold.cyan)
      VampHostCalls.getRoutings.foreach({ case b: DefaultRouting => println(s"${b.name.padTo(25, ' ')}${b.filters.map({ case d: DefaultFilter => s"${d.condition}" }).mkString(", ")}") })

    case "scales" =>
      println("NAME".padTo(25, ' ').bold.cyan + "CPU".padTo(7, ' ').bold.cyan + "MEMORY".padTo(10, ' ').bold.cyan + "INSTANCES".bold.cyan)
      VampHostCalls.getScales.foreach({ case b: DefaultScale => println(s"${b.name.padTo(25, ' ')}${b.cpu.toString.padTo(7, ' ')}${b.memory.toString.padTo(10, ' ')}${b.instances}") })

    case "slas" =>
      println("NAME".bold.cyan)
      VampHostCalls.getSlas.foreach(sla => println(s"${sla.name}"))

    case invalid => terminateWithError(s"Artifact type unknown: '$invalid'")

  }

  private def doInspectCommand(implicit vampHost: String, options: OptionMap) = {
    val artifact: Option[Artifact] = getOptionalParameter(subcommand) match {
      case Some("breed") => VampHostCalls.getBreed(getParameter(name))

      case Some("blueprint") => VampHostCalls.getBlueprint(getParameter(name))

      case Some("deployment") => VampHostCalls.getDeployment(getParameter(name))

      case Some("escalation") => VampHostCalls.getEscalation(getParameter(name))

      case Some("filter") => VampHostCalls.getFilter(getParameter(name))

      case Some("routing") => VampHostCalls.getRouting(getParameter(name))

      case Some("scale") => VampHostCalls.getScale(getParameter(name))

      case Some("sla") => VampHostCalls.getSla(getParameter(name))

      case Some(invalid) => terminateWithError(s"Artifact type unknown: '$invalid'")
        None

      case None => terminateWithError(s"Artifact & name are required")
        None

    }
    printArtifact(artifact)
  }


  private def doDeployCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _: DeployCommand =>
      getBlueprint() match {
        case Some(blueprintToDeploy: DefaultBlueprint) =>
          getOptionalParameter(deployment) match {
            //Existing deployment
            case Some(nameOfDeployment) =>
              VampHostCalls.getDeploymentAsBlueprint(nameOfDeployment) match {
                case Some(deployedBlueprint: DefaultBlueprint) =>
                  printArtifact(VampHostCalls.updateDeployment(nameOfDeployment, artifactToYaml(blueprintToDeploy)))
                case _ => terminateWithError(s"Deployment not found")
              }
            // New deployment
            case None =>
              printArtifact(VampHostCalls.deploy(artifactToYaml(blueprintToDeploy)))
          }
        case _ => terminateWithError("No usable blueprint found.")
      }

    case _ => unhandledCommand _
  }


  def getBlueprint()(implicit vampHost: String, options: OptionMap): Option[Blueprint] = getOptionalParameter(name) match {
    case Some(nameOfBlueprint) => VampHostCalls.getBlueprint(nameOfBlueprint)
    case None => Some(BlueprintReader.read(readFileContent))
  }

  def mergeBlueprints(sourceBlueprint: DefaultBlueprint, additionalBlueprint: DefaultBlueprint): DefaultBlueprint = sourceBlueprint.copy(
    clusters = sourceBlueprint.clusters ++ additionalBlueprint.clusters,
    endpoints = sourceBlueprint.endpoints ++ additionalBlueprint.endpoints,
    environmentVariables = sourceBlueprint.environmentVariables ++ additionalBlueprint.environmentVariables
  )

  private def doOtherCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _: InfoCommand => println(VampHostCalls.info.getOrElse(""))

    case _: HelpCommand => showHelp(HelpCommand())

    case _: VersionCommand => println(s"CLI version: " + s"${getClass.getPackage.getImplementationVersion}".yellow.bold)

    case x: UnknownCommand => terminateWithError(s"Unknown command '${x.name}'")

    case _ => unhandledCommand _
  }


  private def doCreateCommand(implicit vampHost: String, options: OptionMap) = getParameter(name) match {
    case "breed" => printArtifact(VampHostCalls.createBreed(readFileContent))
    case "blueprint" => printArtifact(VampHostCalls.createBlueprint(readFileContent))
    case "escalation" => printArtifact(VampHostCalls.createEscalation(readFileContent))
    case "filter" => printArtifact(VampHostCalls.createFilter(readFileContent))
    case "routing" => printArtifact(VampHostCalls.createRouting(readFileContent))
    case "scale" => printArtifact(VampHostCalls.createScale(readFileContent))
    case "sla" => printArtifact(VampHostCalls.createSla(readFileContent))
    case invalid => terminateWithError(s"Unsupported artifact '$invalid'")
  }

  private def doUpdateCommand(implicit vampHost: String, options: OptionMap) = getOptionalParameter(subcommand) match {
    case Some("breed") => printArtifact(VampHostCalls.updateBreed(getParameter(name), readFileContent))
    case Some("blueprint") => printArtifact(VampHostCalls.updateBlueprint(getParameter(name), readFileContent))
    case Some("escalation") => printArtifact(VampHostCalls.updateEscalation(getParameter(name), readFileContent))
    case Some("filter") => printArtifact(VampHostCalls.updateFilter(getParameter(name), readFileContent))
    case Some("routing") => printArtifact(VampHostCalls.updateRouting(getParameter(name), readFileContent))
    case Some("scale") => printArtifact(VampHostCalls.updateScale(getParameter(name), readFileContent))
    case Some("sla") => printArtifact(VampHostCalls.updateSla(getParameter(name), readFileContent))
    case Some(invalid) => terminateWithError(s"Artifact type unknown: '$invalid'")
      None
    case None => terminateWithError("Artifact & name are required")
  }


  private def doGenerateCommand(implicit vampHost: String, options: OptionMap) = {
    val fileContent = readOptionalFileContent
    printArtifact(getParameter(name) match {
      case "breed" =>
        //TODO implement parameters
        val startWith: Breed = fileContent match {
          case Some(content: String) => BreedReader.read(content)
          case None => emptyBreed
        }

        startWith match {
          case db: DefaultBreed =>
            val dep = getOptionalParameter(deployable) match {
              case Some(dep: String) => Deployable(dep)
              case None => db.deployable
            }
            Some(db.copy(deployable = dep))
          case x => Some(x)
        }

      case "blueprint" =>
        //TODO implement parameters for sla / endpoint / env
        val startWith: Blueprint = fileContent match {
          case Some(content: String) => BlueprintReader.read(content)
          case None => emptyBlueprint
        }

        val myScale: Option[Scale] = getOptionalParameter(scale).flatMap(s => Some(ScaleReference(name = s)))
        val myRouting: Option[Routing] = getOptionalParameter(routing).flatMap(s => Some(RoutingReference(name = s)))


        //val mySla: Option[Sla]= getOptionalParameter(scale).flatMap(s=> Some(SlaReference(name = s)))
        startWith match {
          case bp: DefaultBlueprint =>
            val newCluster = getOptionalParameter(cluster) flatMap (clusterName =>
              getOptionalParameter(breed) flatMap (breedName =>
                Some(List(Cluster(name = clusterName, services = List(Service(breed = BreedReference(breedName), scale = myScale, routing = myRouting)), sla = None)))
                )
              )
            Some(bp.copy(clusters = bp.clusters ++ newCluster.getOrElse(List.empty)))
          case x => Some(x)
        }

      case "escalation" =>
        //TODO implement
        None
      case "filter" =>
        fileContent match {
          case Some(content: String) => Some(FilterReader.read(content))
          case None => Some(emptyFilter)
        }
      case "routing" =>
        fileContent match {
          case Some(content: String) => Some(RoutingReader.read(content))
          case None => Some(emptyRouting)
        }
      case "scale" =>
        fileContent match {
          case Some(content: String) => Some(ScaleReader.read(content))
          case None => Some(emptyScale)
        }
      case "sla" =>
        //TODO implement
        None
      case invalid => terminateWithError(s"Unsupported artifact '$invalid'")
        None
    }
    )
  }

  private def emptyBreed = DefaultBreed(name = "", deployable = Deployable(""), ports = List.empty, environmentVariables = List.empty, constants = List.empty, dependencies = Map.empty)

  private def emptyBlueprint = DefaultBlueprint(name = "", clusters = List.empty, endpoints = List.empty, environmentVariables = List.empty)

  private def emptyScale = DefaultScale(name = "", cpu = 0.0, memory = 0.0, instances = 0)

  private def emptyRouting = DefaultRouting(name = "", weight = None, filters = List.empty)

  private def emptyFilter = DefaultFilter(name = "", condition = "")

  private def doMergeCommand(implicit vampHost: String, options: OptionMap) = getOptionalParameter(deployment) match {
    case Some(deploymentId) =>
      VampHostCalls.getDeploymentAsBlueprint(deploymentId) match {
        case Some(deployedBlueprint: DefaultBlueprint) =>
          getBlueprint() match {
            case Some(bp: DefaultBlueprint) => printArtifact(Some(mergeBlueprints(deployedBlueprint, bp)))
            case _ => terminateWithError("No usable blueprint found.")
          }
        case _ => terminateWithError(s"Deployment not found")
      }
    case None => getOptionalParameter(name) match {
      case Some(blueprintName) =>
        VampHostCalls.getBlueprint(blueprintName) match {
          case Some(existingBlueprint: DefaultBlueprint) =>
            getBlueprint() match {
              case Some(bp: DefaultBlueprint) => printArtifact(Some(mergeBlueprints(existingBlueprint, bp)))
              case _ => terminateWithError("No usable blueprint found.")
            }
          case _ => terminateWithError(s"Blueprint not found")
        }
      case None => terminateWithError("No blueprint name specified")
    }
    case _ => terminateWithError(s"No deployment or blueprint specified")
  }


  private def doDeleteCommand(implicit vampHost: String, options: OptionMap) = getOptionalParameter(subcommand) match {

    case Some("breed") => VampHostCalls.deleteBreed(getParameter(name))

    case Some("blueprint") => VampHostCalls.deleteBlueprint(getParameter(name))

    case Some("escalation") => VampHostCalls.deleteEscalation(getParameter(name))

    case Some("filter") => VampHostCalls.deleteFilter(getParameter(name))

    case Some("routing") => VampHostCalls.deleteRouting(getParameter(name))

    case Some("scale") => VampHostCalls.deleteScale(getParameter(name))

    case Some("sla") => VampHostCalls.deleteSla(getParameter(name))

    case Some(invalid) => terminateWithError(s"Unsupported artifact '$invalid'")

    case None => terminateWithError(s"Artifact & name are required")
  }

  private def doUndeployCommand(implicit vampHost: String, options: OptionMap) = println(VampHostCalls.undeploy(getParameter(name)).getOrElse(""))


  private def unhandledCommand(command: CliCommand) = terminateWithError(s"Unhandled command '${command.name}'")


  private def readOptionalFileContent(implicit options: OptionMap): Option[String] = getOptionalParameter('file) match {
    case Some(fileName) => Some(Source.fromFile(fileName).getLines().mkString("\n"))
    case None => getOptionalParameter('stdin) match {
      case Some(value) => Some(Source.stdin.getLines().mkString("\n"))
      case None => None
    }
  }

  private def readFileContent(implicit options: OptionMap): String = readOptionalFileContent match {
    case Some(content) => content
    case None => terminateWithError("No file specified")
      ""
  }

  private def printArtifact(artifact: Option[Artifact])(implicit options: OptionMap) = {
    getOptionalParameter(json) match {
      case None => artifact.foreach(a => println(artifactToYaml(a)))
      case _ => println(VampHostCalls.prettyJson(artifact))
    }
  }

  private def artifactToYaml(artifact: Artifact): String = {
    def toJson(any: Any) = {
      any match {
        case value: AnyRef => write(value)
        case value => write(value.toString)
      }
    }
    new Yaml().dumpAs(new Yaml().load(toJson(artifact)), Tag.MAP, FlowStyle.BLOCK)
  }

  private def getVampHost(implicit options: Map[Symbol, String]): Option[String] =
    getOptionalParameter(host) match {
      case Some(vampHost: String) => Some(vampHost)
      case _ =>
        println("Sorry, I don't know to which Vamp host I should talk.".bold.red)
        println()
        println("Setup a host in the VAMP_HOST environment variable, or use the --host command line argument.")
        println("Please check http://vamp.io/installation for further details")
        sys.exit(1)
    }

}
