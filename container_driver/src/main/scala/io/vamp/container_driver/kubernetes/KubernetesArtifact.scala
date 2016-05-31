package io.vamp.container_driver.kubernetes

import java.net.URLEncoder

trait KubernetesArtifact {

  protected def labels2json(labels: Map[String, String]) = {
    val l = labels.map { case (k, v) ⇒ s""""$k": "$v"""" } mkString ", "
    s""""labels": {$l}"""
  }

  protected def labelSelector(labels: Map[String, String]) = {
    s"labelSelector=${URLEncoder.encode(labels.map { case (k, v) ⇒ s"$k=$v" } mkString ",", "UTF-8")}"
  }
}
