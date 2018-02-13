package io.vamp.container_driver.kubernetes

import io.vamp.container_driver.Docker
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

case class KubernetesDeploymentRequest(
    name:       String,
    docker:     Docker,
    replicas:   Int,
    cpu:        Double,
    mem:        Int,
    privileged: Boolean,
    env:        Map[String, String],
    cmd:        List[String],
    args:       List[String],
    labels:     Map[String, String],
    dialect:    Map[String, Any]    = Map()
) extends KubernetesArtifact {

  override def toString: String = {

    val container: Map[String, Any] = Map[String, Any](
      "image" → docker.image,
      "name" → name,
      "env" → env.map({ case (n, v) ⇒ Map[String, Any]("name" → n, "value" → v) }),
      "ports" → docker.portMappings.map(pm ⇒ Map[String, Any](
        "name" → s"p${pm.containerPort}", "containerPort" → pm.containerPort, "protocol" → pm.protocol.toUpperCase
      )),
      "args" → args,
      "command" → cmd,
      "resources" → Map[String, Any](
        "requests" → Map[String, Any](
          "cpu" → cpu,
          "memory" → s"${mem}M"
        )
      ),
      "securityContext" → Map[String, Any]("privileged" → privileged)
    )

    val containerDialect: Map[String, Any] = (dialect.getOrElse("containers", List()) match {
      case l: List[_] ⇒ l.headOption.getOrElse(Map()).asInstanceOf[Map[String, Any]]
      case _          ⇒ Map[String, Any]()
    }).filterNot { case (k, _) ⇒ container.contains(k) }

    val deployment = Map(
      "apiVersion" → "extensions/v1beta1",
      "kind" → "Deployment",
      "metadata" → Map("name" → name),
      "spec" → Map(
        "replicas" → replicas,
        "template" → Map(
          "metadata" → labels2map(labels),
          "spec" → (
            dialect ++ Map(
              "containers" → List(
                containerDialect ++ container
              )
            )
          )
        )
      )
    )

    write(deployment)(DefaultFormats)
  }
}
