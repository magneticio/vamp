package io.vamp.core.cli.commands

import io.vamp.core.cli.backend.VampHostCalls
import io.vamp.core.cli.commandline.{ConsoleHelper, Parameters}
import io.vamp.core.model.artifact._
import io.vamp.core.model.serialization.CoreSerializationFormat
import org.json4s.native.Serialization._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

import scala.io.Source

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
      case CommandType.Update => doUpdateCommand(command)
      case CommandType.Deploy => doDeployCommand(command)
      case CommandType.Other => doOtherCommand(command)
    }
  }

  private def doListCommand(implicit vampHost: String, options: OptionMap) = getParameter(name) match {
    case "breeds" =>
      println("NAME".padTo(25, ' ').bold.cyan + "DEPLOYABLE".bold.cyan)
      VampHostCalls.getBreeds.foreach({ case b: DefaultBreed => println(s"${b.name.padTo(25, ' ')}${b.deployable.name}") })

    case "blueprints" =>
      println("NAME".padTo(40, ' ').bold.cyan + "ENDPOINTS".bold.cyan)
      VampHostCalls.getBlueprints.foreach(blueprint => println(s"${blueprint.name.padTo(40, ' ')}${blueprint.endpoints.map(e => s"${e.name} -> ${e.value.get}").mkString(", ")}"))

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
    case _: DeployBreedCommand =>
      val deploymentId: String = getParameter(deployment)
      VampHostCalls.getDeploymentAsBlueprint(deploymentId) match {
        case Some(bp: DefaultBlueprint) =>
          VampHostCalls.getBreed(getParameter(name)) match {
            case Some(deployableBreed: DefaultBreed) =>
              val mergedBlueprint = mergeBreedInCluster(
                blueprint = bp,
                clusterName = getParameter(cluster),
                breed = deployableBreed,
                routing = getOptionalParameter(routing).flatMap(VampHostCalls.getRouting),
                scale = getOptionalParameter(scale).flatMap(VampHostCalls.getScale)
              )
              VampHostCalls.updateDeployment(deploymentId, mergedBlueprint) match {
                case Some(dep) => println(dep.name)
                case None => terminateWithError("Updating deployment failed")
              }
            case undeployableBreed => terminateWithError(s"Breed '$undeployableBreed' not usable")
          }
        case _ => // Deployment not found

      }

    case _: DeployBlueprintCommand => println(NotImplemented)

    case _ => unhandledCommand _
  }

  private def doOtherCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _: InfoCommand => println(VampHostCalls.info.getOrElse(""))

    case _: HelpCommand => showHelp(HelpCommand())

    case _: VersionCommand => println(s"CLI version: " + s"${getClass.getPackage.getImplementationVersion}".yellow.bold)

    case _: CloneBreedCommand =>
      VampHostCalls.getBreed(getParameter(name)) match {
        case Some(sourceBreed: DefaultBreed) =>
          val response = VampHostCalls.createBreed(getOptionalParameter(deployable) match {
            case Some(deployableName) => sourceBreed.copy(name = getParameter(destination), deployable = Deployable(deployableName))
            case None => sourceBreed.copy(name = getParameter(destination))
          })
          println(response)
        case _ => terminateWithError("Source breed not found")
      }

    case x: UnknownCommand => terminateWithError(s"Unknown command '${x.name}'")

    case _ => unhandledCommand _
  }


  private def doCreateCommand(implicit vampHost: String, options: OptionMap) = getParameter(name) match {
    case "breed" =>  printArtifact(VampHostCalls.createBreed(readFileContent))
    case "blueprint" =>  printArtifact(VampHostCalls.createBlueprint(readFileContent))
    case "escalation" =>  printArtifact(VampHostCalls.createEscalation(readFileContent))
    case "filter" =>  printArtifact(VampHostCalls.createFilter(readFileContent))
    case "routing" => printArtifact(VampHostCalls.createRouting(readFileContent))
    case "scale" =>  printArtifact(VampHostCalls.createScale(readFileContent))
    case "sla" =>  printArtifact(VampHostCalls.createSla(readFileContent))
    case invalid => terminateWithError(s"Unsupported artifact '$invalid'")
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

  private def doUpdateCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _ => unhandledCommand _
  }


  private def unhandledCommand(command: CliCommand) = terminateWithError(s"Unhandled command '${command.name}'")

  private def readFileContent(implicit options: OptionMap): String = getOptionalParameter('file) match {
    case Some(fileName) => Source.fromFile(fileName).getLines().mkString("\n")
    case None => Source.stdin.getLines().mkString("\n")
  }


  private def mergeBreedInCluster(blueprint: DefaultBlueprint, clusterName: String, breed: DefaultBreed, routing: Option[Routing], scale: Option[Scale]): DefaultBlueprint =
    blueprint.copy(clusters = blueprint.clusters.filter(_.name != clusterName) ++
      blueprint.clusters.filter(_.name == clusterName).map(c => c.copy(services = c.services ++ List(Service(breed = breed, scale = scale, routing = routing))))
    )


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

  private def getVampHost(implicit options: Map[Symbol, String]) : Option[String] =
    getOptionalParameter(host)  match {
      case Some(vampHost: String) => Some(vampHost)
      case _=>
        println("Sorry, I don't know to which Vamp host I should talk.".bold.red)
        println()
        println("Setup a host in the VAMP_HOST environment variable, or use the --host command line argument.")
        println("Please check http://vamp.io/installation for further details")
        sys.exit(1)
    }

}
