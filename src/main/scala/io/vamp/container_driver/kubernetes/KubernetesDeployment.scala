package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.http.RestClient
import io.vamp.container_driver._
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.concurrent.Future

trait KubernetesDeployment extends KubernetesArtifact {
  this: KubernetesContainerDriver with ActorLogging ⇒

  private val deploymentServiceLabel = "deployment-service"

  private lazy val podUrl = s"$apiUrl/api/v1/namespaces/default/pods"

  private lazy val replicaSetUrl = s"$apiUrl/apis/extensions/v1beta1/namespaces/default/replicasets"

  private lazy val deploymentUrl = s"$apiUrl/apis/extensions/v1beta1/namespaces/default/deployments"

  protected def schema: Enumeration

  protected def labels(id: String) = Map("vamp" -> deploymentServiceLabel, deploymentServiceLabel -> id)

  protected def pods(id: String) = s"$podUrl?${labelSelector(labels(id))}"

  protected def replicas(id: String) = s"$replicaSetUrl?${labelSelector(labels(id))}"

  protected def allContainerServices: Future[List[ContainerService]] = {
    log.debug(s"kubernetes get all")
    RestClient.get[KubernetesApiResponse](deploymentUrl, apiHeaders).flatMap(deployments ⇒ containerServices(deployments))
  }

  private def containerServices(deployments: KubernetesApiResponse): Future[List[ContainerService]] = Future.sequence {
    deployments.items.flatMap {
      case item ⇒
        val name = item.metadata.name
        val scale: Option[DefaultScale] = item.spec.template.flatMap(_.spec.containers.headOption).map(_.resources.requests).map(request ⇒
          DefaultScale("", Quantity.of(request.cpu), MegaByte.of(request.memory), item.spec.replicas.getOrElse(1))
        )
        val ports = item.spec.template.flatMap(_.spec.containers.headOption).map(_.ports.map(_.containerPort)).getOrElse(Nil)

        if (name.split(nameDelimiter).length == 2 && scale.isDefined) {
          RestClient.get[KubernetesApiResponse](pods(name), apiHeaders).map { pods ⇒
            val instances = pods.items.map { pod ⇒
              ContainerInstance(pod.metadata.name, pod.status.podIP.getOrElse(""), ports, pod.status.phase.contains("Running"))
            }
            ContainerService(nameMatcher(name), scale.get, instances)
          } :: Nil

        } else Nil
    }
  }

  protected def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {

    validateSchemaSupport(service.breed.deployable.schema, schema)

    val id = appId(deployment, service.breed)
    if (update) log.info(s"kubernetes update app: $id") else log.info(s"kubernetes create app: $id")

    val privileged = service.arguments.find(_.privileged).exists(_.value.toBoolean)

    val app = KubernetesApp(
      name = id,
      docker = docker(deployment, cluster, service, service.breed.deployable.definition.get),
      replicas = service.scale.get.instances,
      cpu = service.scale.get.cpu.value,
      mem = Math.round(service.scale.get.memory.value).toInt,
      privileged = privileged,
      env = environment(deployment, cluster, service),
      cmd = None,
      args = Nil,
      labels = labels(id)
    )

    if (update) RestClient.put[Any](s"$deploymentUrl/$id", app.toString, apiHeaders) else RestClient.post[Any](deploymentUrl, app.toString, apiHeaders)
  }

  protected def undeploy(deployment: Deployment, service: DeploymentService) = {

    val id = appId(deployment, service.breed)

    log.info(s"kubernetes delete app: $id")

    for {

      deployment ← RestClient.delete(s"$deploymentUrl/$id", apiHeaders)

      replicas ← RestClient.get[KubernetesApiResponse](replicas(id), apiHeaders).flatMap { replicas ⇒
        Future.sequence {
          replicas.items.map(item ⇒ RestClient.delete(s"$replicaSetUrl/${item.metadata.name}", apiHeaders))
        }
      }

      pods ← RestClient.get[KubernetesApiResponse](pods(id), apiHeaders).flatMap { pods ⇒
        Future.sequence {
          pods.items.map(item ⇒ RestClient.delete(s"$podUrl/${item.metadata.name}", apiHeaders))
        }
      }

    } yield deployment :: replicas :: pods :: Nil
  }
}
