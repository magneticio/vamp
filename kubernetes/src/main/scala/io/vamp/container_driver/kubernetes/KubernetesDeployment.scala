package io.vamp.container_driver.kubernetes

import com.google.gson.reflect.TypeToken
import com.typesafe.scalalogging.LazyLogging
import io.kubernetes.client.models._
import io.vamp.common.akka.CommonActorLogging
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver.{ ContainerDriver, _ }
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.collection.JavaConverters._
import scala.util.Try

object KubernetesDeployment {
  val dialect = "kubernetes"
}

trait KubernetesDeployment extends KubernetesArtifact with LazyLogging {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  private val timeout = 3
  private val deploymentServiceIdLabel = "deployment-service"

  private val workflowIdLabel = "workflow"

  override protected def supportedDeployableTypes: List[DeployableType] = RktDeployableType :: DockerDeployableType :: Nil

  protected def schema: Enumeration

  protected def labels(id: String, value: String) = Map(ContainerDriver.labelNamespace() → value, ContainerDriver.withNamespace(value) → id)

  protected def containerServices(deploymentServices: List[DeploymentServices], equalityRequest: ServiceEqualityRequest): List[ContainerService] = {
    deploymentServices.flatMap { deploymentService ⇒
      deploymentService.services.map { service ⇒
        val id = appId(deploymentService.deployment, service.breed)
        log.debug(s"kubernetes get $id")
        k8sClient.cache.readWithCache(
          K8sCache.deployments,
          id,
          () ⇒ k8sClient.extensionsV1beta1Api.readNamespacedDeploymentStatus(id, customNamespace, null)
        ).flatMap { deployment ⇒
            containers(
              id,
              deploymentServiceIdLabel,
              Try(deployment.getSpec.getTemplate.getSpec.getContainers.asScala).toOption.getOrElse(Nil),
              deployment.getSpec.getReplicas
            ) map { containerService ⇒
                val k8sContainer = deployment.getSpec.getTemplate.getSpec.getContainers.asScala.map(c ⇒ c.getName → c).toMap.get(id)
                val cluster = deploymentService.deployment.clusters.find { c ⇒ c.services.exists { s ⇒ s.breed.name == service.breed.name } }
                val processClusterEquality = k8sContainer.isDefined && cluster.isDefined

                val equality = ServiceEqualityResponse(
                  deployable = !equalityRequest.deployable || (k8sContainer.isDefined && checkDeployable(service, k8sContainer.get)),
                  ports = !equalityRequest.ports || (processClusterEquality && checkPorts(deploymentService.deployment, cluster.get, service, k8sContainer.get)),
                  environmentVariables = !equalityRequest.environmentVariables || (processClusterEquality && checkEnvironmentVariables(deploymentService.deployment, cluster.get, service, k8sContainer.get))
                )

                ContainerService(deploymentService.deployment, service, Option(containerService), None, equality)
              }
          } getOrElse ContainerService(deploymentService.deployment, service, None)
      }
    }
  }

  protected def containerWorkflow(workflow: Workflow): ContainerWorkflow = {
    val id = appId(workflow)
    log.debug(s"kubernetes get $id")
    k8sClient.cache.readWithCache(
      K8sCache.deployments,
      id,
      () ⇒ k8sClient.extensionsV1beta1Api.readNamespacedDeploymentStatus(id, customNamespace, null)
    ).map { deployment ⇒
        logger.debug("KubernetesDeployment - Retrieved workflow deployment")
        ContainerWorkflow(
          workflow,
          containers(
            id,
            workflowIdLabel,
            Try(deployment.getSpec.getTemplate.getSpec.getContainers.asScala).recover {
              case t ⇒
                logger.error("Couldn't retrieve containers for WorkFlow", t)
                null
            }.toOption.getOrElse(Nil),
            deployment.getSpec.getReplicas
          )
        )
      } getOrElse ContainerWorkflow(workflow, None)
  }

  private def containers(id: String, selector: String, v1Containers: Seq[V1Container], replicas: Int): Option[Containers] = Try {
    val ports = v1Containers.headOption.map(_.getPorts.asScala.map(_.getContainerPort.toInt)).getOrElse(Nil).toList

    val scale: Option[DefaultScale] = v1Containers.headOption.flatMap { item ⇒
      val request = item.getResources.getRequests.asScala
      for {
        cpu ← request.get("cpu")
        memory ← request.get("memory")
      } yield {
        logger.debug("Quantity: cpu: {} memory: {}", cpu.toSuffixedString, memory.toSuffixedString)
        DefaultScale(Quantity.of(cpu.toSuffixedString), MegaByte.of(memory.toSuffixedString), replicas)
      }
    }
    if (scale.isDefined) {
      logger.debug("KubernetesDeployment - scale is defined {}", scale.toString)

      val instances = pods(id, selector).map { pod ⇒
        {

          logger.debug("KubernetesDeployment - Scale is defined and pod phase is {}", pod.getStatus.getPhase)

          ContainerInstance(pod.getMetadata.getName, Option(pod.getStatus.getPodIP).getOrElse(""), ports, Try(pod.getStatus.getPhase.contains("Running")).toOption.getOrElse(false))
        }
      }.toList
      Option(Containers(scale.get, instances))
    }
    else {
      logger.debug("KubernetesDeployment - scale is undefined {}", scale.toString)
      None
    }
  }.recover {
    case t ⇒
      logger.error("KubernetesDeployment - Failed to retrieve containers", t)
      None
  }.toOption.flatten

  private def checkDeployable(service: DeploymentService, container: V1Container): Boolean = service.breed.deployable.definition == container.getImage

  private def checkPorts(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, container: V1Container): Boolean = {
    Try(container.getPorts.asScala.map(_.getContainerPort.toInt).toSet).toOption.getOrElse(Set[Int]()) == portMappings(deployment, cluster, service, "").map(_.containerPort).toSet
  }

  private def checkEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, container: V1Container): Boolean = {
    Try(container.getEnv.asScala.map(e ⇒ e.getName → e.getValue).toMap).toOption.getOrElse(Map[String, String]()) == environment(deployment, cluster, service)
  }

  protected def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Unit = {
    validateDeployable(service.breed.deployable)

    val id = appId(deployment, service.breed)
    if (update) log.debug(s"kubernetes update app: $id") else log.debug(s"kubernetes create app: $id")

    val (local, dialect) = (deployment.dialects.get(KubernetesDeployment.dialect), cluster.dialects.get(KubernetesDeployment.dialect), service.dialects.get(KubernetesDeployment.dialect)) match {
      case (_, _, Some(d))       ⇒ Some(service) → d
      case (_, Some(d), None)    ⇒ None → d
      case (Some(d), None, None) ⇒ None → d
      case _                     ⇒ None → Map()
    }

    deploy(
      id = id,
      docker = docker(deployment, cluster, service),
      scale = service.scale.get,
      environmentVariables = environment(deployment, cluster, service),
      labels = labels(deployment, cluster, service) ++ labels(id, deploymentServiceIdLabel),
      update = update,
      dialect = interpolate(deployment, local, dialect.asInstanceOf[Map[String, Any]])
    )
  }

  protected def undeploy(deployment: Deployment, service: DeploymentService): Unit = {
    val id = appId(deployment, service.breed)
    log.debug(s"kubernetes delete app: $id")
    deleteDeployment(id)
  }

  protected def deploy(workflow: Workflow, update: Boolean): Unit = {

    validateDeployable(workflow.breed.asInstanceOf[DefaultBreed].deployable)

    val id = appId(workflow)
    if (update) log.debug(s"kubernetes update workflow: ${workflow.name}") else log.debug(s"kubernetes create workflow: ${workflow.name}")

    val scale = workflow.scale.get.asInstanceOf[DefaultScale]
    val dialect = workflow.dialects.getOrElse(KubernetesDeployment.dialect, Map())

    deploy(
      id = id,
      docker = docker(workflow),
      scale = scale,
      environmentVariables = environment(workflow),
      labels = labels(workflow) ++ labels(id, workflowIdLabel),
      update = update,
      dialect = dialect.asInstanceOf[Map[String, Any]]
    )
  }

  protected def undeploy(workflow: Workflow): Unit = {
    val id = appId(workflow)
    log.debug(s"kubernetes delete workflow: ${workflow.name}")
    deleteDeployment(id)
  }

  private def deploy(id: String, docker: Docker, scale: DefaultScale, environmentVariables: Map[String, String], labels: Map[String, String], update: Boolean, dialect: Map[String, Any]): Unit = {
    val app = KubernetesDeploymentRequest(
      name = id,
      docker = docker,
      replicas = scale.instances,
      cpu = scale.cpu.value,
      mem = Math.round(scale.memory.value).toInt,
      privileged = docker.privileged,
      env = environmentVariables,
      cmd = Nil,
      args = Nil,
      labels = labels,
      dialect = dialect
    )

    if (update)
      k8sClient.cache.writeWithCache(
        K8sCache.update,
        K8sCache.deployments,
        id,
        () ⇒ k8sClient.extensionsV1beta1Api.replaceNamespacedDeployment(
          id,
          customNamespace,
          k8sClient.extensionsV1beta1Api.getApiClient.getJSON.deserialize(app.toString, new TypeToken[ExtensionsV1beta1Deployment]() {}.getType),
          null
        )
      )
    else k8sClient.cache.writeWithCache(
      K8sCache.create,
      K8sCache.deployments,
      id,
      () ⇒ k8sClient.extensionsV1beta1Api.createNamespacedDeployment(
        customNamespace,
        k8sClient.extensionsV1beta1Api.getApiClient.getJSON.deserialize(app.toString, new TypeToken[ExtensionsV1beta1Deployment]() {}.getType),
        null
      )
    )
  }

  protected def podsForAllNamespaces(): Seq[V1Pod] = {
    k8sClient.cache.readAllWithCache(
      K8sCache.pods,
      "*",
      () ⇒ Try(k8sClient.coreV1Api.listPodForAllNamespaces(null, null, false, null, null, null, null, timeout, false).getItems.asScala).toOption.getOrElse(Nil)
    )
  }

  protected def pods(id: String, value: String): Seq[V1Pod] = {
    val selector = labelSelector(labels(id, value))
    k8sClient.cache.readAllWithCache(
      K8sCache.pods,
      selector,
      () ⇒ Try(k8sClient.coreV1Api.listNamespacedPod(customNamespace, null, null, null, false, selector, null, null, timeout, false).getItems.asScala).toOption.getOrElse(Nil)
    )
  }

  protected def replicas(id: String, value: String): Seq[V1beta1ReplicaSet] = {
    val selector = labelSelector(labels(id, value))
    k8sClient.cache.readAllWithCache(
      K8sCache.replicaSets,
      selector,
      () ⇒ Try(k8sClient.extensionsV1beta1Api.listNamespacedReplicaSet(customNamespace, null, null, null, false, selector, null, null, timeout, false).getItems.asScala).toOption.getOrElse(Nil)
    )
  }

  protected def createDeployment(request: String): Unit = {
    log.debug(s"Creating Kubernetes deployment")
    k8sClient.extensionsV1beta1Api.createNamespacedDeployment(
      customNamespace,
      k8sClient.extensionsV1beta1Api.getApiClient.getJSON.deserialize(request, new TypeToken[ExtensionsV1beta1Deployment]() {}.getType),
      null
    )
  }

  protected def updateDeployment(request: String): Unit = {
    log.debug(s"Creating Kubernetes deployment")
    val parsed = k8sClient.extensionsV1beta1Api.getApiClient.getJSON.deserialize(request, new TypeToken[ExtensionsV1beta1Deployment]() {}.getType)
    log.info("Request: " + request)
    log.info("Parsed request: " + parsed)
    k8sClient.extensionsV1beta1Api.patchNamespacedDeployment(
      s"vamp-gateway-agent",
      customNamespace,
      request,
      null
    )
  }

  protected def deleteDeployment(name: String): Unit = {
    log.debug(s"Deleting Kubernetes deployment $name")
    k8sClient.cache.writeWithCache(
      K8sCache.delete,
      K8sCache.deployments,
      name,
      () ⇒ k8sClient.extensionsV1beta1Api.deleteNamespacedDeployment(name, customNamespace, new V1DeleteOptions().propagationPolicy("Background"), null, null, null, null)
    )
  }
}
