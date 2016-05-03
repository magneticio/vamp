package io.vamp.container_driver.kubernetes

import io.vamp.container_driver.Docker

case class KubernetesApp(name: String, docker: Docker, replicas: Int, cpu: Double, mem: Int, privileged: Boolean, env: Map[String, String], cmd: Option[String], args: List[String]) {

  override def toString =
    s"""
       |{
       |  "apiVersion": "extensions/v1beta1",
       |  "kind": "Deployment",
       |  "metadata": {
       |    "name": "$name"
       |  },
       |  "spec": {
       |    "replicas": $replicas,
       |    "template": {
       |      "metadata": {
       |        "labels": {
       |          "app": "$name"
       |        }
       |      },
       |      "spec": {
       |        "containers": [{
       |          "image": "${docker.image}",
       |          "name": "$name",
       |          "env": [${env.map({ case (n, v) ⇒ s"""{"name": "$n", "value": "$v"}""" }).mkString(", ")}],
       |          "ports": [${docker.portMappings.map(pm ⇒ s"""{"name": "p${pm.containerPort}", "containerPort": ${pm.containerPort}, "protocol": "${pm.protocol.toUpperCase}"}""").mkString(", ")}],
       |          "args": [${args.map(str ⇒ s""""$str"""").mkString(", ")}],
       |          "command": ${if (cmd.isDefined) s"""["${cmd.get}"]""" else "[]"},
       |          "resources": {
       |            "requests": {
       |              "cpu": $cpu,
       |              "memory": "${mem}Mi"
       |            }
       |          },
       |          "securityContext": {
       |            "privileged": $privileged
       |          }
       |        }]
       |      }
       |    }
       |  }
       |}
     """.stripMargin
}

case class KubernetesApiResponse(items: List[KubernetesItem])

case class KubernetesItem(metadata: KubernetesMetadata, spec: KubernetesSpec, status: KubernetesStatus)

case class KubernetesMetadata(name: String)

case class KubernetesSpec(replicas: Option[Int], template: Option[KubernetesTemplate])

case class KubernetesTemplate(spec: KubernetesTemplateSpec)

case class KubernetesTemplateSpec(containers: List[KubernetesContainer])

case class KubernetesContainer(ports: List[KubernetesContainerPort], resources: KubernetesContainerResource)

case class KubernetesContainerPort(containerPort: Int)

case class KubernetesContainerResource(requests: KubernetesContainerResourceRequests)

case class KubernetesContainerResourceRequests(cpu: String, memory: String)

case class KubernetesStatus(phase: Option[String], podIP: Option[String])
