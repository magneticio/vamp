package io.vamp.container_driver.kubernetes

import java.net.URLEncoder

trait KubernetesArtifact {

  protected def labels2json(labels: Map[String, String]) = {
    val l = labels.map { case (k, v) ⇒ s""""$k": "$v"""" } mkString ", "
    s""""labels": {$l}"""
  }

  protected def labels2parameters(labels: Map[String, String]) = {
    labels.map { case (k, v) ⇒ URLEncoder.encode(s"$k=$v", "UTF-8") } mkString ","
  }
}
