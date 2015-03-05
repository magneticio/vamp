package io.magnetic.vamp_core.persistence

import io.magnetic.vamp_core.model.artifact._

/**
 * Created by Matthijs Dekker on 05/03/15.
 */
object TestData {

  val breed = DefaultBreed(
    name = "wp-stackable",
    deployable = Deployable("wordpress"),
    ports = List(
      HttpPort(name ="port8080", alias = Option("HTTP"), value =  Option(8080), direction = Trait.Direction.In),
      HttpPort(name = "port22", alias = Option("SSH"), value = Option(22), direction = Trait.Direction.Out)
    ),
    environmentVariables = List(
      EnvironmentVariable(name="JAVA_HOME",alias=Some("JAVAHOME"), value=Some("/opt/java/bin"),direction = Trait.Direction.In),
      EnvironmentVariable(name="HI_MEM",alias=None, value=Some("64K"),direction = Trait.Direction.Out)
    ),
    dependencies = Map("db" -> BreedReference(name= "mysql"))
  )

  val breed2 = breed.copy(name = "wp-test")

  val breed3 = DefaultBreed(name = "mysql-backup", deployable = Deployable("backup"), environmentVariables = List.empty, ports = List.empty, dependencies = Map.empty)

}
