package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.http.RestClient
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver._
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.workflow.Workflow

import scala.concurrent.Future

trait KubernetesDeployment extends KubernetesArtifact {
  this: KubernetesContainerDriver with ActorLogging ⇒

  private val deploymentServiceIdLabel = "deployment-service-id"

  private val workflowIdLabel = "workflow-id"

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
              RestClient.get[KubernetesApiResponse](pods(id, deploymentServiceIdLabel), apiHeaders).map { pods ⇒
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

    deploy(id, docker(deployment, cluster, service), service.scale.get, environment(deployment, cluster, service), labels(id, deploymentServiceIdLabel) ++ labels(deployment, cluster, service), update)
  }

  protected def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val id = appId(deployment, service.breed)
    log.info(s"kubernetes delete app: $id")
    undeploy(id, deploymentServiceIdLabel)
  }

  protected def retrieve(id: String): Future[Option[Any]] = {
    RestClient.get[KubernetesItem](s"$deploymentUrl/${string2Id(id)}", apiHeaders, logError = false).recover {
      case _ ⇒ None
    } map {
      case None ⇒ None
      case item ⇒ Option(item)
    }
  }

  protected def retrieve(workflow: Workflow): Future[Option[Any]] = retrieve(appId(workflow))

  protected def deploy(workflow: Workflow, update: Boolean): Future[Any] = {

    validateDeployable(workflow.breed.asInstanceOf[DefaultBreed].deployable)

    val id = appId(workflow)
    if (update) log.info(s"kubernetes update workflow: ${workflow.name}") else log.info(s"kubernetes create workflow: ${workflow.name}")

    val scale = workflow.scale.get.asInstanceOf[DefaultScale]

    deploy(id, docker(workflow), scale, environment(workflow), labels(id, workflowIdLabel) ++ labels(workflow), update)
  }

  protected def undeploy(workflow: Workflow): Future[Any] = {
    val id = appId(workflow)
    log.info(s"kubernetes delete workflow: ${workflow.name}")
    undeploy(id, workflowIdLabel)
  }

  private def deploy(id: String, docker: Docker, scale: DefaultScale, environmentVariables: Map[String, String], labels: Map[String, String], update: Boolean): Future[Any] = {

    val app = KubernetesApp(
      name = id,
      docker = docker,
      replicas = scale.instances,
      cpu = scale.cpu.value,
      mem = Math.round(scale.memory.value).toInt,
      privileged = docker.privileged,
      env = environmentVariables,
      cmd = Nil,
      args = Nil,
      labels = labels
    )

    if (update) RestClient.put[Any](s"$deploymentUrl/$id", app.toString, apiHeaders) else RestClient.post[Any](deploymentUrl, app.toString, apiHeaders)
  }

  private def undeploy(id: String, selector: String): Future[Any] = {
    for {

      deployment ← RestClient.delete(s"$deploymentUrl/$id", apiHeaders)

      replicas ← RestClient.get[KubernetesApiResponse](replicas(id, selector), apiHeaders).flatMap { replicas ⇒
        Future.sequence {
          replicas.items.map(item ⇒ RestClient.delete(s"$replicaSetUrl/${item.metadata.name}", apiHeaders))
        }
      }

      pods ← RestClient.get[KubernetesApiResponse](pods(id, selector), apiHeaders).flatMap { pods ⇒
        Future.sequence {
          pods.items.map(item ⇒ RestClient.delete(s"$podUrl/${item.metadata.name}", apiHeaders))
        }
      }

    } yield deployment :: replicas :: pods :: Nil
  }
}
