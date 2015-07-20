package io.vamp.core.cli.commands


import java.io.File

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

  def doCommand(command: CliCommand, subCommand: Option[String])(implicit options: OptionMap): Unit = {

    implicit val vampHost: String = if (command.requiresHostConnection) getVampHost.getOrElse("Host not specified") else "Not required"

    command.commandType match {
      case CommandType.List => doListCommand(subCommand)
      case CommandType.Inspect => doInspectCommand(subCommand)
      case CommandType.Create => doCreateCommand(subCommand)
      case CommandType.Delete => doDeleteCommand(subCommand)
      case CommandType.Generate => doGenerateCommand(subCommand)
      case CommandType.Deploy => doDeployCommand(command)
      case CommandType.Merge => doMergeCommand
      case CommandType.Undeploy => doUndeployCommand
      case CommandType.Update => doUpdateCommand(subCommand)
      case CommandType.Other => doOtherCommand(command)
    }
  }

  private def doListCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {
    case Some("breeds") =>
      println("NAME".padTo(25, ' ').bold.cyan + "DEPLOYABLE".bold.cyan)
      VampHostCalls.getBreeds.foreach({ case b: DefaultBreed => println(s"${b.name.padTo(25, ' ')}${b.deployable.name}") })

    case Some("blueprints") =>
      println("NAME".padTo(40, ' ').bold.cyan + "ENDPOINTS".bold.cyan)
      VampHostCalls.getBlueprints.foreach({ case blueprint: DefaultBlueprint => println(s"${blueprint.name.padTo(40, ' ')}${blueprint.endpoints.map(e => s"${e.name} -> ${e.value.get}").mkString(", ")}") })

    case Some("deployments") =>
      println("NAME".padTo(40, ' ').bold.cyan + "CLUSTERS".bold.cyan)
      VampHostCalls.getDeployments.foreach(deployment => println(s"${deployment.name.padTo(40, ' ')}${deployment.clusters.map(c => s"${c.name}").mkString(", ")}"))

    case Some("escalations") =>
      println("NAME".padTo(25, ' ').bold.cyan + "TYPE".padTo(20, ' ').bold.cyan + "SETTINGS".bold.cyan)
      VampHostCalls.getEscalations.foreach({
        case b: ScaleInstancesEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleCpuEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleMemoryEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: Escalation => println(s"${b.name.padTo(25, ' ')}")
        case x => println(x)
      })

    case Some("filters") =>
      println("NAME".padTo(25, ' ').bold.cyan + "CONDITION".bold.cyan)
      VampHostCalls.getFilters.foreach({ case b: DefaultFilter => println(s"${b.name.padTo(25, ' ')}${b.condition}") })

    case Some("routings") =>
      println("NAME".padTo(25, ' ').bold.cyan + "FILTERS".bold.cyan)
      VampHostCalls.getRoutings.foreach({ case b: DefaultRouting => println(s"${b.name.padTo(25, ' ')}${b.filters.map({ case d: DefaultFilter => s"${d.condition}" }).mkString(", ")}") })

    case Some("scales") =>
      println("NAME".padTo(25, ' ').bold.cyan + "CPU".padTo(7, ' ').bold.cyan + "MEMORY".padTo(10, ' ').bold.cyan + "INSTANCES".bold.cyan)
      VampHostCalls.getScales.foreach({ case b: DefaultScale => println(s"${b.name.padTo(25, ' ')}${b.cpu.toString.padTo(7, ' ')}${b.memory.toString.padTo(10, ' ')}${b.instances}") })

    case Some("slas") =>
      println("NAME".bold.cyan)
      VampHostCalls.getSlas.foreach(sla => println(s"${sla.name}"))

    case Some(invalid) => terminateWithError(s"Artifact type unknown: '$invalid'")
    case None => terminateWithError("Please specify an artifact type")

  }

  private def doInspectCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = {
    val artifact: Option[Artifact] = subCommand match {
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

      case None => terminateWithError("Please specify an artifact type")
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


  private def doCreateCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {
    case Some("breed") => printArtifact(VampHostCalls.createBreed(readFileContent))
    case Some("blueprint") => printArtifact(VampHostCalls.createBlueprint(readFileContent))
    case Some("escalation") => printArtifact(VampHostCalls.createEscalation(readFileContent))
    case Some("filter") => printArtifact(VampHostCalls.createFilter(readFileContent))
    case Some("routing") => printArtifact(VampHostCalls.createRouting(readFileContent))
    case Some("scale") => printArtifact(VampHostCalls.createScale(readFileContent))
    case Some("sla") => printArtifact(VampHostCalls.createSla(readFileContent))
    case Some(invalid) => terminateWithError(s"Unsupported artifact '$invalid'")
    case None => terminateWithError("Please specify an artifact type")
  }

  private def doUpdateCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {
    case Some("breed") => printArtifact(VampHostCalls.updateBreed(getParameter(name), readFileContent))
    case Some("blueprint") => printArtifact(VampHostCalls.updateBlueprint(getParameter(name), readFileContent))
    case Some("escalation") => printArtifact(VampHostCalls.updateEscalation(getParameter(name), readFileContent))
    case Some("filter") => printArtifact(VampHostCalls.updateFilter(getParameter(name), readFileContent))
    case Some("routing") => printArtifact(VampHostCalls.updateRouting(getParameter(name), readFileContent))
    case Some("scale") => printArtifact(VampHostCalls.updateScale(getParameter(name), readFileContent))
    case Some("sla") => printArtifact(VampHostCalls.updateSla(getParameter(name), readFileContent))
    case Some(invalid) => terminateWithError(s"Artifact type unknown: '$invalid'")
      None
    case None => terminateWithError("Please specify an artifact type")
      None
  }



  private def doGenerateCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = {
    val fileContent = readOptionalFileContent
    printArtifact(subCommand match {
      case Some("breed") =>
        //TODO implement parameters for env / constants
        readArtifactStartingPoint[Breed](fileContent, BreedReader, emptyBreed) match {
          case db: DefaultBreed =>
            Some(db
              .copy(
                name = replaceValueString(name,db.name),
                deployable = getOptionalParameter(deployable) match {
                  case Some(dep: String) => Deployable(dep)
                  case None => db.deployable
                })
            )
          case other => Some(other)
        }

      case Some("blueprint") =>
        //TODO implement parameters for sla / endpoint / env

        val myScale: Option[Scale] = getOptionalParameter(scale).flatMap(s => Some(ScaleReference(name = s)))
        val myRouting: Option[Routing] = getOptionalParameter(routing).flatMap(s => Some(RoutingReference(name = s)))

        //val mySla: Option[Sla]= getOptionalParameter(scale).flatMap(s=> Some(SlaReference(name = s)))
        readArtifactStartingPoint[Blueprint](fileContent, BlueprintReader, emptyBlueprint) match {
          case bp: DefaultBlueprint =>
            val newCluster = getOptionalParameter(cluster) flatMap (clusterName =>
              getOptionalParameter(breed) flatMap (breedName =>
                Some(List(Cluster(name = clusterName, services = List(Service(breed = BreedReference(breedName), scale = myScale, routing = myRouting)), sla = None)))
                )
              )
            Some(bp.copy(name = replaceValueString(name,bp.name), clusters = bp.clusters ++ newCluster.getOrElse(List.empty)))
          case other => Some(other)
        }

      case Some("escalation-cpu") =>
        readArtifactStartingPoint[Escalation](fileContent, EscalationReader, emptyScaleCpuEscalation) match {
          case df: ScaleCpuEscalation =>
            Some(
              df.copy(
                name = replaceValueString(name,df.name),
                minimum = replaceValueDouble(minimum, df.minimum),
                maximum = replaceValueDouble(maximum, df.maximum),
                scaleBy = replaceValueDouble(scale_by, df.scaleBy),
                targetCluster = replaceValueOptionalString(target_cluster, df.targetCluster)
              )
            )
          case other => Some(other)
        }


      case Some("escalation-memory") =>
        readArtifactStartingPoint[Escalation](fileContent, EscalationReader, emptyScaleMemoryEscalation) match {
          case df: ScaleMemoryEscalation =>
            Some(
              df.copy(
                name = replaceValueString(name,df.name),
                minimum = replaceValueDouble(minimum, df.minimum),
                maximum = replaceValueDouble(maximum, df.maximum),
                scaleBy = replaceValueDouble(scale_by, df.scaleBy),
                targetCluster = replaceValueOptionalString(target_cluster, df.targetCluster)
              )
            )
          case other => Some(other)
        }

      case Some("escalation_instance") =>
        readArtifactStartingPoint[Escalation](fileContent, EscalationReader, emptyScaleInstanceEscalation) match {
          case df: ScaleInstancesEscalation =>
            Some(
              df.copy(
                name = replaceValueString(name,df.name),
                minimum = replaceValueInt(minimum, df.minimum),
                maximum = replaceValueInt(maximum, df.maximum),
                scaleBy = replaceValueInt(scale_by, df.scaleBy),
                targetCluster = replaceValueOptionalString(target_cluster, df.targetCluster)
              )
            )
          case other => Some(other)
        }

      case Some("filter") =>
        readArtifactStartingPoint[Filter](fileContent, FilterReader, emptyFilter) match {
          case df: DefaultFilter => Some(df.copy(name = replaceValueString(name,df.name)))
          case other => Some(other)
        }
      case Some("routing") =>
        readArtifactStartingPoint[Routing](fileContent, RoutingReader, emptyRouting) match {
          case dr: DefaultRouting => Some(dr.copy(name = replaceValueString(name,dr.name)))
          case other => Some(other)
        }
      case Some("scale") =>
        readArtifactStartingPoint[Scale](fileContent, ScaleReader, emptyScale) match {
          case ds: DefaultScale => Some(ds.copy(name = replaceValueString(name,ds.name)))
          case other => Some(other)
        }
      case Some("sla") =>
        //TODO implement
        None
      case Some(invalid) => terminateWithError(s"Unsupported artifact '$invalid'")
        None
      case None => terminateWithError("Please specify an artifact type")
        None
    }
    )
  }

  private def emptyBreed = DefaultBreed(name = "", deployable = Deployable(""), ports = List.empty, environmentVariables = List.empty, constants = List.empty, dependencies = Map.empty)

  private def emptyBlueprint = DefaultBlueprint(name = "", clusters = List.empty, endpoints = List.empty, environmentVariables = List.empty)

  private def emptyScale = DefaultScale(name = "", cpu = 0.0, memory = 0.0, instances = 0)

  private def emptyRouting = DefaultRouting(name = "", weight = None, filters = List.empty)

  private def emptyFilter = DefaultFilter(name = "", condition = "")

  private def emptyScaleCpuEscalation = ScaleCpuEscalation(name = "", minimum = 0, maximum = 0, scaleBy = 0, targetCluster = None)

  private def emptyScaleMemoryEscalation = ScaleMemoryEscalation(name = "", minimum = 0, maximum = 0, scaleBy = 0, targetCluster = None)

  private def emptyScaleInstanceEscalation = ScaleInstancesEscalation(name = "", minimum = 0, maximum = 0, scaleBy = 0, targetCluster = None)


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


  private def doDeleteCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = subCommand match {

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
    case Some(fileName) => if (java.nio.file.Files.exists(new File(fileName).toPath)) {
      Some(Source.fromFile(fileName).getLines().mkString("\n"))
    } else {
      terminateWithError(s"File '$fileName' not found")
      None
    }
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


  private def readArtifactStartingPoint[A](fileContent: Option[String], reader: YamlReader[A], alternative: A) = fileContent match {
    case Some(content: String) => reader.read(content)
    case None => alternative
  }


  private def replaceValueDouble(parameterName: Symbol, originalValue: Double)(implicit options: OptionMap): Double =
    getOptionalParameter(parameterName) match {
      case Some(v) => try {
        v.toDouble
      } catch {
        case e: Exception => terminateWithError(s"Invalid value $v for ${parameterName.name}")
          0
      }
      case None => originalValue
    }

  private def replaceValueInt(parameterName: Symbol, originalValue: Int)(implicit options: OptionMap): Int =
    getOptionalParameter(parameterName) match {
      case Some(v) => try {
        v.toInt
      } catch {
        case e: Exception => terminateWithError(s"Invalid value $v for ${parameterName.name}")
          0
      }
      case None => originalValue
    }

  private def replaceValueOptionalString(parameterName: Symbol, originalValue: Option[String])(implicit options: OptionMap): Option[String] =
    getOptionalParameter(parameterName) match {
      case Some(v) => Some(v)
      case None => originalValue
    }

  private def replaceValueString(parameterName: Symbol, originalValue: String)(implicit options: OptionMap): String =
    getOptionalParameter(parameterName) match {
      case Some(v) => v
      case None => originalValue
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
