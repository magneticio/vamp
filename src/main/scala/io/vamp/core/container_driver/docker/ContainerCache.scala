package io.vamp.core.container_driver.docker

import scala.async.Async.{ async, await }
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

trait ContainerCache {

  private var containers: Map[String, String] = Map.empty

  protected def addContainerToCache(containerName: String, containerId: Future[String])(implicit executionContext: ExecutionContext) = async {
    containers = containers + (containerName -> await(containerId))
  }

  protected def removeContainerIdFromCache(containerId: String) =
    containers = containers.filter(_._2 != containerId)

  protected def findContainerIdInCache(containerName: String): Option[String] = containers.get(containerName)

}
