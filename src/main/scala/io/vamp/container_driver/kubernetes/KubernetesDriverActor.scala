package io.vamp.container_driver.kubernetes

import com.typesafe.config.ConfigFactory
import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor.{ All, Deploy, Undeploy }
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.concurrent.Future

object KubernetesDriverActor {

  object Schema extends Enumeration {
    val Docker = Value
  }

  KubernetesDriverActor.Schema.values

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  val kubernetesUrl = configuration.getString("kubernetes.url")
}

case class KubernetesDriverInfo(version: Any, paths: Any, api: Any, apis: Any)

class KubernetesDriverActor extends ContainerDriverActor with ContainerDriver with KubernetesDaemonSet {

  import KubernetesDriverActor._

  val kubernetesUrl = KubernetesDriverActor.kubernetesUrl

  protected val nameDelimiter = "-"

  protected val idMatcher = """^[a-z0-9]*$""".r

  protected val deploymentUrl = s"$kubernetesUrl/apis/extensions/v1beta1/namespaces/default/deployments"

  protected def appId(deployment: Deployment, breed: Breed): String = s"${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def artifactName2Id(artifact: Artifact): String = artifact.name match {
    case idMatcher(_*) if artifact.name.length < 32 ⇒ artifact.name
    case _ ⇒ Hash.hexSha1(artifact.name).substring(0, 20)
  }

  def receive = {
    case InfoRequest   ⇒ reply(info)
    case All           ⇒ reply(all)
    case d: Deploy     ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy   ⇒ reply(undeploy(u.deployment, u.service))
    case ds: DaemonSet ⇒ daemonSet(ds)
    case any           ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = for {
    paths ← RestClient.get[Any](s"$kubernetesUrl") map {
      case map: Map[_, _] ⇒ map.headOption.map { case (_, value) ⇒ value }
      case any            ⇒ any
    }
    api ← RestClient.get[Any](s"$kubernetesUrl/api")
    apis ← RestClient.get[Any](s"$kubernetesUrl/apis")
    version ← RestClient.get[Any](s"$kubernetesUrl/version")
  } yield {
    ContainerInfo("kubernetes", KubernetesDriverInfo(version, paths, api, apis))
  }

  private def all: Future[List[ContainerService]] = {
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

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    validateSchemaSupport(service.breed.deployable.schema, Schema)

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

  private def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    log.info(s"kubernetes delete app: $id")
    RestClient.delete(s"$deploymentUrl/$id")
  }
}
