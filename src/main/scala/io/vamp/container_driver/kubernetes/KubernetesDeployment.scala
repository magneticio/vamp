package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.http.RestClient
import io.vamp.container_driver._
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.concurrent.Future

trait KubernetesDeployment {
  this: KubernetesContainerDriver with ActorLogging ⇒

  protected val deploymentUrl = s"$kubernetesUrl/apis/extensions/v1beta1/namespaces/default/deployments"

  protected def schema: Enumeration

  protected def allContainerServices: Future[List[ContainerService]] = {
    log.debug(s"kubernetes get all")
    RestClient.get[KubernetesApiResponse](deploymentUrl).flatMap(deployments ⇒ containerServices(deployments))
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
          val podUrl = s"$kubernetesUrl/api/v1/namespaces/default/pods?labelSelector=app%3D$name"

          RestClient.get[KubernetesApiResponse](podUrl).map { pods ⇒
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
      args = Nil
    )

    if (update) RestClient.put[Any](s"$deploymentUrl/$id", app.toString) else RestClient.post[Any](deploymentUrl, app.toString)
  }

  protected def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    log.info(s"kubernetes delete app: $id")
    RestClient.delete(s"$deploymentUrl/$id")
  }
}
