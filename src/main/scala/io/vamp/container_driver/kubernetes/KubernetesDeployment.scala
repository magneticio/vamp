package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.http.RestClient
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver._
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.concurrent.Future

trait KubernetesDeployment extends KubernetesArtifact {
  this: KubernetesContainerDriver with ActorLogging ⇒

  private val deploymentLabel = "deployment"

  private val deploymentServiceLabel = "deployment-service"

  private lazy val podUrl = s"$apiUrl/api/v1/namespaces/default/pods"

  private lazy val replicaSetUrl = s"$apiUrl/apis/extensions/v1beta1/namespaces/default/replicasets"

  private lazy val deploymentUrl = s"$apiUrl/apis/extensions/v1beta1/namespaces/default/deployments"

  override protected def supportedDeployableTypes = List(DockerDeployable)

  protected def schema: Enumeration

  protected def labels(id: String, value: String) = Map("vamp" -> value, value -> id)

  protected def pods(id: String, value: String) = s"$podUrl?${labelSelector(labels(id, value))}"

  protected def replicas(id: String, value: String) = s"$replicaSetUrl?${labelSelector(labels(id, value))}"

  protected def allContainerServices(deploymentServices: List[DeploymentServices]): Future[List[ContainerService]] = {
    log.debug(s"kubernetes get all")
    RestClient.get[KubernetesApiResponse](deploymentUrl, apiHeaders).flatMap { deployments ⇒
      containerServices(deploymentServices, deployments)
    }
  }

  private def containerServices(deploymentServices: List[DeploymentServices], response: KubernetesApiResponse): Future[List[ContainerService]] = Future.sequence {

    val deployed = response.items.map(item ⇒ item.metadata.name -> item).toMap

    deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).flatMap {
      case (deployment, service) ⇒

        val id = appId(deployment, service.breed)

        deployed.get(id) match {

          case Some(item) ⇒

            val ports = item.spec.template.flatMap(_.spec.containers.headOption).map(_.ports.map(_.containerPort)).getOrElse(Nil)
            val scale: Option[DefaultScale] = item.spec.template.flatMap(_.spec.containers.headOption).map(_.resources.requests).map(request ⇒
              DefaultScale("", Quantity.of(request.cpu), MegaByte.of(request.memory), item.spec.replicas.getOrElse(1))
            )

            if (scale.isDefined) {
              RestClient.get[KubernetesApiResponse](pods(id, deploymentServiceLabel), apiHeaders).map { pods ⇒
                val instances = pods.items.map { pod ⇒
                  ContainerInstance(pod.metadata.name, pod.status.podIP.getOrElse(""), ports, pod.status.phase.contains("Running"))
                }
                ContainerService(deployment, service, Option(Containers(scale.get, instances)))
              } :: Nil

            } else Nil

          case None ⇒ Future.successful(ContainerService(deployment, service, None)) :: Nil
        }
    }
  }

  protected def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {

    validateDeployable(service.breed.deployable)

    val id = appId(deployment, service.breed)
    if (update) log.info(s"kubernetes update app: $id") else log.info(s"kubernetes create app: $id")

    val privileged = service.arguments.find(_.privileged).exists(_.value.toBoolean)

    val app = KubernetesApp(
      name = id,
      docker = docker(deployment, cluster, service, service.breed.deployable.definition),
      replicas = service.scale.get.instances,
      cpu = service.scale.get.cpu.value,
      mem = Math.round(service.scale.get.memory.value).toInt,
      privileged = privileged,
      env = environment(deployment, cluster, service),
      cmd = Nil,
      args = Nil,
      labels = labels(id, deploymentServiceLabel) ++ labels(deployment, cluster, service)
    )

    if (update) RestClient.put[Any](s"$deploymentUrl/$id", app.toString, apiHeaders) else RestClient.post[Any](deploymentUrl, app.toString, apiHeaders)
  }

  protected def undeploy(deployment: Deployment, service: DeploymentService) = {

    val id = appId(deployment, service.breed)

    log.info(s"kubernetes delete app: $id")

    for {

      deployment ← RestClient.delete(s"$deploymentUrl/$id", apiHeaders)

      replicas ← RestClient.get[KubernetesApiResponse](replicas(id, deploymentServiceLabel), apiHeaders).flatMap { replicas ⇒
        Future.sequence {
          replicas.items.map(item ⇒ RestClient.delete(s"$replicaSetUrl/${item.metadata.name}", apiHeaders))
        }
      }

      pods ← RestClient.get[KubernetesApiResponse](pods(id, deploymentServiceLabel), apiHeaders).flatMap { pods ⇒
        Future.sequence {
          pods.items.map(item ⇒ RestClient.delete(s"$podUrl/${item.metadata.name}", apiHeaders))
        }
      }

    } yield deployment :: replicas :: pods :: Nil
  }

  protected def retrieve(id: String): Future[Option[Any]] = {
    RestClient.get[KubernetesItem](s"$deploymentUrl/${string2Id(id)}", apiHeaders, logError = false).recover {
      case _ ⇒ None
    } map {
      case None ⇒ None
      case item ⇒ Option(item)
    }
  }

  protected def deploy(app: DockerApp, update: Boolean): Future[Any] = app.container match {
    case Some(docker) ⇒

      val id = string2Id(app.id)

      val docker = app.container.get

      val deployment = KubernetesApp(
        name = id,
        docker = docker,
        replicas = app.instances,
        cpu = app.cpu,
        mem = app.memory,
        privileged = docker.privileged,
        env = app.environmentVariables,
        cmd = app.command,
        args = app.arguments,
        labels = labels(id, deploymentLabel) ++ app.labels
      )

      if (update) {
        log.info(s"kubernetes update app: ${app.id} [$id]")
        RestClient.put[Any](s"$deploymentUrl/$id", deployment.toString, apiHeaders)
      } else {
        log.info(s"kubernetes create app: ${app.id} [$id]")
        RestClient.post[Any](s"$deploymentUrl", deployment.toString, apiHeaders)
      }

    case None ⇒ Future.successful(None)
  }

  protected def undeploy(id: String) = {

    val deploymentId = string2Id(id)

    retrieve(deploymentId).map {

      case Some(_) ⇒

        log.info(s"kubernetes delete app: $id [$deploymentId]")

        for {

          deployment ← RestClient.delete(s"$deploymentUrl/$deploymentId", apiHeaders, logError = false)

          replicas ← RestClient.get[KubernetesApiResponse](replicas(deploymentId, deploymentLabel), apiHeaders).flatMap { replicas ⇒
            Future.sequence {
              replicas.items.map(item ⇒ RestClient.delete(s"$replicaSetUrl/${item.metadata.name}", apiHeaders))
            }
          }

          pods ← RestClient.get[KubernetesApiResponse](pods(deploymentId, deploymentLabel), apiHeaders).flatMap { pods ⇒
            Future.sequence {
              pods.items.map(item ⇒ RestClient.delete(s"$podUrl/${item.metadata.name}", apiHeaders))
            }
          }

        } yield deployment :: replicas :: pods :: Nil

      case _ ⇒
    }
  }
}
