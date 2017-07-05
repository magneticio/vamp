package io.vamp.container_driver.kubernetes

import io.vamp.common.akka.CommonActorLogging
import io.vamp.container_driver.{ ContainerDriver, Docker }

import scala.concurrent.Future

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

  private lazy val url = s"$apiUrl/apis/batch/v1/namespaces/${namespace.name}/jobs"

  protected def createJob(job: Job, labels: Map[String, String] = Map()): Future[Any] = {
    val request =
      s"""
         |{
         |  "apiVersion": "batch/v1",
         |  "kind": "Job",
         |  "metadata": {
         |    "name": "${job.name}",
         |    ${labels2json(labels + (ContainerDriver.withNamespace("name") → job.name))}
         |  },
         |  "spec": {
         |    "template": {
         |      "metadata": {
         |        ${labels2json(Map(ContainerDriver.labelNamespace() → "job", ContainerDriver.withNamespace("job") → job.name))}
         |      },
         |      "spec": {
         |        "containers": [{
         |          "image": "${job.docker.image}",
         |          "name": "${job.name}",
         |          "env": [${job.environmentVariables.map({ case (n, v) ⇒ s"""{"name": "$n", "value": "$v"}""" }).mkString(", ")}],
         |          "ports": [${job.docker.portMappings.map(pm ⇒ s"""{"containerPort": ${pm.containerPort}, "name": "p${pm.containerPort}"}""").mkString(", ")}],
         |          "args": [${job.arguments.map(str ⇒ s""""$str"""").mkString(", ")}],
         |          "command": [${job.command.map(str ⇒ s""""$str"""").mkString(", ")}],
         |          "resources": {
         |            "requests": {
         |              "cpu": ${job.cpu},
         |              "memory": ${job.mem}
         |            }
         |          },
         |          "securityContext": {
         |            "privileged": ${job.docker.privileged}
         |          }
         |        }],
         |        "restartPolicy": "Never"
         |      }
         |    }
         |  }
         |}
      """.stripMargin

    retrieve(url, job.name,
      () ⇒ {
        log.debug(s"Job exists: ${job.name}")
        Future.successful(false)
      },
      () ⇒ {
        log.info(s"Creating job: ${job.name}")
        httpClient.post[Any](url, request, apiHeaders)
      })
  }
}
