package io.vamp.core.cli


import io.vamp.core.model.artifact._

object Boot extends Parameters {

  def main(args: Array[String]) {
    if (args.length == 0) {
      showHelp(HelpCommand())
      sys.exit(0)
    }
    implicit val options = readParameters(args.tail)
    implicit val vampHost: String = getParameter(host)

    if (options.contains(help)) showHelp(string2Command(args.head))

    string2Command(args.head) match {
      case _: BreedsCommand =>
        println("NAME".padTo(25, ' ') + "DEPLOYABLE")
        VampHostCalls.getBreeds.foreach(breed =>
          println(s"${breed.name.padTo(25, ' ')}${breed.deployable.name}")
        )

      case _: BlueprintsCommand =>
        println("NAME".padTo(40, ' ') + "ENDPOINTS")
        VampHostCalls.getBlueprints.foreach(blueprint =>
          println(s"${blueprint.name.padTo(40, ' ')}${blueprint.endpoints.map(e => s"${e.name} -> ${e.value.get}").mkString(", ")}")
        )

      case _: InspectBreedCommand =>
        println(VampHostCalls.prettyJson(VampHostCalls.getBreed(getParameter(name))))

      case _: InspectBlueprintCommand =>
        println(VampHostCalls.prettyJson(VampHostCalls.getBlueprint(getParameter(name))))

      case _: CloneBreedCommand =>
        println(VampHostCalls.prettyJson(
          VampHostCalls.getBreed(getParameter(name)).map(sourceBreed => {
            VampHostCalls.createBreed(getOptionalParameter(deployable) match {
              case Some(deployableName) => sourceBreed.copy(name = getParameter(destination), deployable = Deployable(deployableName))
              case None => sourceBreed.copy(name = getParameter(destination))
            })
          })
        ))

      case _: DeployBreed =>
        //TODO add support for routing & scale parameters
        val deploymentId: String = getParameter(deployment)
        VampHostCalls.getDeploymentAsBlueprint(deploymentId) match {
          case Some(bp) =>
            val mergedBlueprint = {
              VampHostCalls.getBreed(getParameter(name)).map(b =>
                mergeBreedInCluster(blueprint = bp, clusterName = getParameter(cluster), breed = b, routing = None, scale = None)
              )
            }
            //TODO Report back the name of the deployment
            val result = VampHostCalls.updateDeployment(deploymentId, mergedBlueprint.get)

          case None => // Deployment not found

        }
      case _: DeployBlueprintCommand => println(NotImplemented)
      case _: RemoveBreedCommand => VampHostCalls.deleteBreed(getParameter(name))
      case _: RemoveBlueprintCommand => println(NotImplemented)
      case _: HelpCommand => showHelp(HelpCommand())
      case x: UnknownCommand => terminateWithError(s"Unknown command '${x.name}'")
    }

  }

  private def mergeBreedInCluster(blueprint: DefaultBlueprint, clusterName: String, breed: DefaultBreed, routing: Option[DefaultRouting], scale: Option[DefaultScale]): DefaultBlueprint =
    blueprint.copy(clusters = blueprint.clusters.filter(_.name != clusterName) ++
      blueprint.clusters.filter(_.name == clusterName).map(c => c.copy(services = c.services ++ List(Service(breed = breed, scale = scale, routing = routing))))
    )


}


