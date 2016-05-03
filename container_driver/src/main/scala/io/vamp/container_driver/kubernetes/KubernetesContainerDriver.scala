package io.vamp.container_driver.kubernetes

import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient
import io.vamp.container_driver.ContainerDriver
import io.vamp.model.artifact._

import scala.concurrent.Future

trait KubernetesContainerDriver extends ContainerDriver {

  protected def kubernetesUrl: String

  protected val nameDelimiter = "-"

  protected val idMatcher = """^[a-z0-9]*$""".r

  protected def appId(deployment: Deployment, breed: Breed): String = s"${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def artifactName2Id(artifact: Artifact): String = artifact.name match {
    case idMatcher(_*) if artifact.name.length < 32 ⇒ artifact.name
    case _ ⇒ Hash.hexSha1(artifact.name).substring(0, 20)
  }

  protected def retrieve(url: String, name: String, exists: () ⇒ Unit, notExists: () ⇒ Future[Any]): Future[Any] = RestClient.get[KubernetesApiResponse](url).map {
    case ds ⇒ ds.items.map(_.metadata.name).contains(name)
  } flatMap {
    case true ⇒
      exists()
      Future.successful(false)
    case false ⇒ notExists()
  }
}
