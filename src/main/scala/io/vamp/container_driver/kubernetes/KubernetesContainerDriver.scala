package io.vamp.container_driver.kubernetes

import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient
import io.vamp.container_driver.ContainerDriver
import io.vamp.model.artifact._

import scala.concurrent.Future

trait KubernetesContainerDriver extends ContainerDriver {

  protected def apiUrl: String

  protected def apiHeaders: List[(String, String)]

  protected val nameDelimiter = "-"

  protected val idMatcher = """^[a-z0-9]*$""".r

  protected def appId(deployment: Deployment, breed: Breed): String = s"${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def artifactName2Id(artifact: Artifact): String = string2Id(artifact.name)

  protected def string2Id(id: String): String = id match {
    case idMatcher(_*) if id.length < 32 ⇒ id
    case _                               ⇒ Hash.hexSha1(id).substring(0, 20)
  }

  protected def retrieve(url: String, name: String, exists: () ⇒ Future[Any], notExists: () ⇒ Future[Any]): Future[Any] = {
    RestClient.get[KubernetesItem](s"$url/$name", apiHeaders, logError = false).recover {
      case _ ⇒ notExists()
    } map {
      case _ ⇒ exists()
    }
  }

  protected def exists(url: String, name: String): Future[Boolean] = {
    RestClient.get[KubernetesItem](s"$url/$name", apiHeaders, logError = false).recover {
      case _ ⇒ false
    } map {
      case _ ⇒ true
    }
  }
}
