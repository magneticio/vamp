package io.vamp.container_driver.kubernetes

import io.vamp.common.akka.CommonActorLogging
import io.vamp.common.http.HttpClient
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver.{ ContainerDriver, _ }
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.concurrent.Future

object KubernetesDeployment {
  val dialect = "kubernetes"
}

trait KubernetesDeployment extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  private val deploymentServiceIdLabel = "deployment-service"

  private val workflowIdLabel = "workflow"

  private lazy val podUrl = s"$apiUrl/api/v1/namespaces/${namespace.name}/pods"

  private lazy val replicaSetUrl = s"$apiUrl/apis/extensions/v1beta1/namespaces/${namespace.name}/replicasets"

  private lazy val deploymentUrl = s"$apiUrl/apis/extensions/v1beta1/namespaces/${namespace.name}/deployments"

  override protected def supportedDeployableTypes: List[DeployableType] = RktDeployableType :: DockerDeployableType :: Nil

  protected def schema: Enumeration

  protected def labels(id: String, value: String) = Map(ContainerDriver.labelNamespace() → value, ContainerDriver.withNamespace(value) → id)

  protected def pods(id: String, value: String) = s"$podUrl?${labelSelector(labels(id, value))}"

  protected def replicas(id: String, value: String) = s"$replicaSetUrl?${labelSelector(labels(id, value))}"

  protected def allContainerServices(deploymentServices: List[DeploymentServices], equalityRequest: ServiceEqualityRequest): Future[List[ContainerService]] = {
    log.debug(s"kubernetes get all")
    httpClient.get[KubernetesApiResponse](deploymentUrl, apiHeaders).flatMap { deployments ⇒
      containerServices(equalityRequest, deploymentServices, deployments)
    }
  }

  protected def containerWorkflow(workflow: Workflow): Future[ContainerWorkflow] = {
    log.debug(s"kubernetes get all")
    val id = appId(workflow)

    httpClient.get[KubernetesItem](s"$deploymentUrl/$id", apiHeaders, HttpClient.jsonContentType, logError = false).recover {
      case _ ⇒ None
    }.flatMap {
      case item: KubernetesItem ⇒ containers(id, item, workflowIdLabel).map(ContainerWorkflow(workflow, _))
      case _                    ⇒ Future.successful(ContainerWorkflow(workflow, None))
    }
  }

  private def containerServices(equalityRequest: ServiceEqualityRequest, deploymentServices: List[DeploymentServices], response: KubernetesApiResponse): Future[List[ContainerService]] = {
    val deployed = response.items.map(item ⇒ item.metadata.name → item).toMap
    val k8sContainers = response.items.flatMap(_.spec.template).flatMap(_.spec.containers).map(c ⇒ c.name → c).toMap
    Future.sequence {
      deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).map {
        case (deployment, service) ⇒

          val id = appId(deployment, service.breed)
          deployed.get(id) match {
            case Some(item) ⇒
              containers(id, item, deploymentServiceIdLabel).map {
                case Some(cs) ⇒
                  val k8sContainer = k8sContainers.get(id)
                  val cluster = deployment.clusters.find { c ⇒ c.services.exists { s ⇒ s.breed.name == service.breed.name } }
                  val processClusterEquality = k8sContainer.isDefined && cluster.isDefined

                  val equality = ServiceEqualityResponse(
                    deployable = !equalityRequest.deployable || (k8sContainer.isDefined && checkDeployable(service, k8sContainer.get)),
                    ports = !equalityRequest.ports || (processClusterEquality && checkPorts(deployment, cluster.get, service, k8sContainer.get)),
                    environmentVariables = !equalityRequest.environmentVariables || (processClusterEquality && checkEnvironmentVariables(deployment, cluster.get, service, k8sContainer.get))
                  )

                  ContainerService(deployment, service, Option(cs), None, equality)

                case None ⇒ ContainerService(deployment, service, None)
              }
            case None ⇒ Future.successful(ContainerService(deployment, service, None))
          }
      }
    }
  }

  private def checkDeployable(service: DeploymentService, container: KubernetesContainer): Boolean = {
    service.breed.deployable.definition == container.image
  }

  private def checkPorts(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, container: KubernetesContainer): Boolean = {
    container.ports.map(_.containerPort).toSet == portMappings(deployment, cluster, service, "").map(_.containerPort).toSet
  }

  private def checkEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, container: KubernetesContainer): Boolean = {
    container.env.map(e ⇒ e.name → e.value).toMap == environment(deployment, cluster, service)
  }

  private def containers(id: String, item: KubernetesItem, selector: String): Future[Option[Containers]] = {
    val ports = item.spec.template.flatMap(_.spec.containers.headOption).map(_.ports.map(_.containerPort)).getOrElse(Nil)
    val scale: Option[DefaultScale] = item.spec.template.flatMap(_.spec.containers.headOption).map(_.resources.requests).map { request ⇒
      DefaultScale(Quantity.of(request.cpu), MegaByte.of(request.memory), item.spec.replicas.getOrElse(1))
    }

    if (scale.isDefined) {
      httpClient.get[KubernetesApiResponse](pods(id, selector), apiHeaders).map { pods ⇒
        val instances = pods.items.map { pod ⇒
          ContainerInstance(pod.metadata.name, pod.status.podIP.getOrElse(""), ports, pod.status.phase.contains("Running"))
        }
        Option(Containers(scale.get, instances))
      }
    }
    else Future.successful(None)
  }

  protected def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    validateDeployable(service.breed.deployable)

    val id = appId(deployment, service.breed)
    if (update) log.info(s"kubernetes update app: $id") else log.info(s"kubernetes create app: $id")

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

  protected def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val id = appId(deployment, service.breed)
    log.info(s"kubernetes delete app: $id")
    undeploy(id, deploymentServiceIdLabel)
  }

  protected def retrieve(id: String): Future[Option[Any]] = {
    httpClient.get[KubernetesItem](s"$deploymentUrl/${string2Id(id)}", apiHeaders, logError = false).recover {
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

  protected def undeploy(workflow: Workflow): Future[Any] = {
    val id = appId(workflow)
    log.info(s"kubernetes delete workflow: ${workflow.name}")
    undeploy(id, workflowIdLabel)
  }

  private def deploy(id: String, docker: Docker, scale: DefaultScale, environmentVariables: Map[String, String], labels: Map[String, String], update: Boolean, dialect: Map[String, Any]): Future[Any] = {
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
      labels = labels,
      dialect = dialect
    )

    if (update) httpClient.put[Any](s"$deploymentUrl/$id", app.toString, apiHeaders) else httpClient.post[Any](deploymentUrl, app.toString, apiHeaders)
  }

  private def undeploy(id: String, selector: String): Future[Any] = {
    for {

      deployment ← httpClient.delete(s"$deploymentUrl/$id", apiHeaders)

      replicas ← httpClient.get[KubernetesApiResponse](replicas(id, selector), apiHeaders).flatMap { replicas ⇒
        Future.sequence {
          replicas.items.map(item ⇒ httpClient.delete(s"$replicaSetUrl/${item.metadata.name}", apiHeaders))
        }
      }

      pods ← httpClient.get[KubernetesApiResponse](pods(id, selector), apiHeaders).flatMap { pods ⇒
        Future.sequence {
          pods.items.map(item ⇒ httpClient.delete(s"$podUrl/${item.metadata.name}", apiHeaders))
        }
      }

    } yield deployment :: replicas :: pods :: Nil
  }
}
