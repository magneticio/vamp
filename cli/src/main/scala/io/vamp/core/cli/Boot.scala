package io.vamp.core.cli


import io.vamp.core.model.artifact._

object Boot extends Parameters {

  def main(args: Array[String]) {
    if (args.length == 0) {
      showHelp(HelpCommand())
      sys.exit(0)
    }
    implicit val options = readParameters(args.tail)

    if (options.contains(help)) showHelp(string2Command(args.head))
    implicit val vampHost: String = getParameter(host)

    string2Command(args.head) match {
      case _: BreedsCommand =>
        println("NAME".padTo(25, ' ') + "DEPLOYABLE")
        VampHostCalls.getBreeds.foreach(breed =>  breed match {
          case b: DefaultBreed => println(s"${b.name.padTo(25, ' ')}${b.deployable.name}")
          case _ => println(s"${breed.name.padTo(25, ' ')}")
        })

      case _: BlueprintsCommand =>
        println("NAME".padTo(40, ' ') + "ENDPOINTS")
        VampHostCalls.getBlueprints.foreach(blueprint =>
          println(s"${blueprint.name.padTo(40, ' ')}${blueprint.endpoints.map(e => s"${e.name} -> ${e.value.get}").mkString(", ")}")
        )

      case _: DeploymentsCommand =>
        println("NAME".padTo(40, ' ') + "CLUSTERS")
        VampHostCalls.getDeployments.foreach(deployment =>
          println(s"${deployment.name.padTo(40, ' ')}${deployment.clusters.map(c => s"${c.name}").mkString(", ")}")
        )

      case _: InfoCommand =>
        println(VampHostCalls.info)

      case _: InspectBreedCommand =>
        println(VampHostCalls.prettyJson(VampHostCalls.getBreed(getParameter(name))))
      //println(VampHostCalls.getBreed(getParameter(name)))

      case _: InspectBlueprintCommand =>
        println(VampHostCalls.prettyJson(VampHostCalls.getBlueprint(getParameter(name))))

      case _: InspectDeploymentCommand =>
        println(VampHostCalls.prettyJson(VampHostCalls.getDeployment(getParameter(name))))

      case _: CloneBreedCommand =>
        VampHostCalls.getBreed(getParameter(name)) match {
          case sourceBreed: DefaultBreed =>
            val response = VampHostCalls.createBreed(getOptionalParameter(deployable) match {
              case Some(deployableName) => sourceBreed.copy(name = getParameter(destination), deployable = Deployable(deployableName))
              case None => sourceBreed.copy(name = getParameter(destination))
            })
            println(response)
          case _ => terminateWithError("Source breed not found")
        }

      case _: DeployBreedCommand =>
        //TODO add support for routing & scale parameters
        val deploymentId: String = getParameter(deployment)
        VampHostCalls.getDeploymentAsBlueprint(deploymentId) match {
          case Some(bp) =>
            VampHostCalls.getBreed(getParameter(name)) match {
              case deployableBreed: DefaultBreed =>
                val mergedBlueprint = mergeBreedInCluster(blueprint = bp, clusterName = getParameter(cluster), breed = deployableBreed, routing = None, scale = None)
                VampHostCalls.updateDeployment(deploymentId, mergedBlueprint) match {
                  case Some(dep) => println(dep.name)
                  case None => terminateWithError("Updating deployment failed")
                }
              case undeployableBreed => terminateWithError(s"Breed '$undeployableBreed' not usable")
            }
          case None => // Deployment not found

        }
      case _: DeployBlueprintCommand => println(NotImplemented)
      case _: RemoveBreedCommand => VampHostCalls.deleteBreed(getParameter(name))
      case _: RemoveBlueprintCommand => println(NotImplemented)

      case _: SlasCommand =>
        println("NAME")
        VampHostCalls.getSlas.foreach(sla =>
          println(s"${sla.name}")
        )

      case _: HelpCommand => showHelp(HelpCommand())
      case x: UnknownCommand => terminateWithError(s"Unknown command '${x.name}'")
    }
    sys.exit(0)
  }

  private def mergeBreedInCluster(blueprint: DefaultBlueprint, clusterName: String, breed: DefaultBreed, routing: Option[DefaultRouting], scale: Option[DefaultScale]): DefaultBlueprint =
    blueprint.copy(clusters = blueprint.clusters.filter(_.name != clusterName) ++
      blueprint.clusters.filter(_.name == clusterName).map(c => c.copy(services = c.services ++ List(Service(breed = breed, scale = scale, routing = routing))))
    )


}


