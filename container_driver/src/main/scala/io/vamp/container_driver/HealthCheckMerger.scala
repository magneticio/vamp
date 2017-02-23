package io.vamp.container_driver

import io.vamp.model.artifact._

/**
 * Provides the behavior for merging between breed-, service- and cluster-level HealthChecks
 */
trait HealthCheckMerger {

  /**
   * Merges three different optional HealthCheck lists
   * Order of precedence from high to low: Service, Cluster, Breed
   * An empty serviceLevel List denotes that there should be NO health checks for that service
   */
  def retrieveHealthChecks(cluster: DeploymentCluster, service: DeploymentService): List[HealthCheck] =
    mergeHealthChecks(
      service.breed.healthChecks,
      service.healthChecks,
      cluster.healthChecks,
      service.breed.ports)

  private[container_driver] def mergeHealthChecks(
    breedLevelOpt:   Option[List[HealthCheck]],
    serviceLevelOpt: Option[List[HealthCheck]],
    clusterLevelOpt: Option[List[HealthCheck]],
    ports:           List[Port]): List[HealthCheck] =
    serviceLevelOpt.map { serviceHealthChecks ⇒
      if (serviceHealthChecks.isEmpty)
        serviceHealthChecks
      else
        serviceHealthChecks.++ {
          clusterLevelOpt
            .getOrElse(List())
            .filter { hc ⇒
              // Path of health check can NOT be part of the service level health checks and the port needs to exist
              serviceHealthChecks.exists(_.path != hc.path) && ports.exists(_.name == hc.port)
            }
        }
    }.getOrElse {
      clusterLevelOpt.map { clusterLevel ⇒
        clusterLevel.filter(hc ⇒ ports.exists(_.name == hc.port))
      }.getOrElse(breedLevelOpt.getOrElse(List()))
    }

  /**
   * Retrieves the health checks for a Workflow.
   * Workflow level has precedence over breed level health checks.
   */
  def retrieveHealthChecks(workflow: Workflow): List[HealthCheck] =
    mergeHealthChecks(workflow.healthChecks, Some(fromBreed(workflow)))

  private[container_driver] def mergeHealthChecks(
    workflowLevelOpt: Option[List[HealthCheck]],
    breedLevelOpt:    Option[List[HealthCheck]]): List[HealthCheck] =
    workflowLevelOpt.getOrElse(breedLevelOpt.getOrElse(List()))

  private def fromBreed(workflow: Workflow): List[HealthCheck] =
    workflow.breed match {
      case defaultBreed: DefaultBreed ⇒ defaultBreed.healthChecks.getOrElse(List())
      case _                          ⇒ List()
    }
}
