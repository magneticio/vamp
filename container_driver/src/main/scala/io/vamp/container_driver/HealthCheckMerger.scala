package io.vamp.container_driver

import io.vamp.model.artifact.{ HealthCheck, Port }

/**
 * Provides the behavior for merging between breed-, service- and cluster-level HealthChecks
 */
trait HealthCheckMerger {

  /**
   * Merges three different optional HealthCheck lists
   * Order of precedence from high to low: Service, Cluster, Breed
   * An empty serviceLevel List denotes that there should be NO health checks for that service
   */
  def mergeHealthChecks(
    breedLevel:   Option[List[HealthCheck]],
    serviceLevel: Option[List[HealthCheck]],
    clusterLevel: Option[List[HealthCheck]],
    ports:        List[Port]): List[HealthCheck] =
    serviceLevel.map { serviceHealthChecks ⇒
      if (serviceHealthChecks.isEmpty)
        serviceHealthChecks
      else
        serviceHealthChecks.++ {
          clusterLevel
            .getOrElse(List())
            .filter { hc ⇒
              // Path of health check can NOT be part of the service level health checks and the port needs to exist
              serviceHealthChecks.exists(_.path != hc.path) && ports.exists(_.name == hc.port)
            }
        }
    }.getOrElse {
      val clusterHealthChecks = clusterLevel
        .getOrElse(List())
        .filter(hc ⇒ ports.exists(_.name == hc.port))

      clusterHealthChecks ++ breedLevel
        .getOrElse(List())
        .filter { bhc ⇒
          clusterHealthChecks.exists(chc ⇒ chc.port != bhc.port && chc.path != bhc.path)
        }
    }
}
