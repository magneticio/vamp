package io.vamp.container_driver.kubernetes

import io.kubernetes.client.models.{ V1SecurityContext, _ }
import io.vamp.common.akka.CommonActorLogging
import io.vamp.container_driver.{ ContainerDriver, Docker }

import scala.collection.JavaConverters._

case class Job(
  name:                 String,
  docker:               Docker,
  cpu:                  Double,
  mem:                  Int,
  environmentVariables: Map[String, String],
  arguments:            List[String]        = Nil,
  command:              List[String]        = Nil
)

trait KubernetesJob extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  protected def createJob(job: Job, labels: Map[String, String] = Map()): Unit = {
    k8sClient.cache.readRequestWithCache(
      K8sCache.job,
      job.name,
      () ⇒ k8sClient.batchV1Api.readNamespacedJobStatus(job.name, namespace.name, null)
    ) match {
        case Some(_) ⇒ log.debug(s"Job exists: ${job.name}")
        case None ⇒
          log.info(s"Creating job: ${job.name}")

          val request = new V1Job
          val metadata = new V1ObjectMeta
          request.setMetadata(metadata)
          metadata.setName(job.name)
          metadata.setLabels(filterLabels(labels + (ContainerDriver.withNamespace("name") → job.name)).asJava)

          val spec = new V1JobSpec
          request.setSpec(spec)
          val template = new V1PodTemplateSpec
          spec.setTemplate(template)

          val templateMetadata = new V1ObjectMeta
          templateMetadata.setLabels(filterLabels(Map(ContainerDriver.labelNamespace() → "job", ContainerDriver.withNamespace("job") → job.name)).asJava)
          template.setMetadata(templateMetadata)

          val podSpec = new V1PodSpec
          template.setSpec(podSpec)

          val container = new V1Container
          podSpec.setContainers(List(container).asJava)
          podSpec.setRestartPolicy("Never")

          container.setName(job.name)
          container.setImage(job.docker.image)
          container.setPorts(job.docker.portMappings.map { pm ⇒
            val port = new V1ContainerPort
            port.setName(s"p${pm.containerPort}")
            port.setContainerPort(pm.containerPort)
            port
          }.asJava)
          container.setCommand(job.command.asJava)

          val resources = new V1ResourceRequirements
          container.setResources(resources)
          resources.setRequests(Map("cpu" → job.cpu.toString, "memory" → job.mem.toString).asJava)

          container.setEnv(job.environmentVariables.map {
            case (k, v) ⇒
              val env = new V1EnvVar
              env.setName(k)
              env.setValue(v)
              env
          }.toList.asJava)

          container.setArgs(job.arguments.asJava)

          val context = new V1SecurityContext
          container.setSecurityContext(context)
          context.setPrivileged(job.docker.privileged)

          k8sClient.cache.writeRequestWithCache(
            K8sCache.job,
            job.name,
            () ⇒ k8sClient.batchV1Api.createNamespacedJob(namespace.name, request, null)
          )
      }
  }
}
