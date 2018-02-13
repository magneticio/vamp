package io.vamp.container_driver.kubernetes

trait KubernetesArtifact {

  protected def filterLabels(labels: Map[String, String]): Map[String, String] = labels.filter {
    case (k, _) ⇒ k.matches("(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?") && k.length < 64
  } filter {
    case (_, v) ⇒ v.isEmpty || (v.matches("^[a-zA-Z0-9].*[a-zA-Z0-9]$") && v.length < 64)
  } map {
    case (k, v) ⇒ k → v.replaceAll("[^a-zA-Z0-9\\._-]", "_")
  }

  protected def labels2map(labels: Map[String, String]): Map[String, Map[String, String]] = {
    val l: Map[String, String] = labels.filter {
      case (k, _) ⇒ k.matches("(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?") && k.length < 64
    } filter {
      case (_, v) ⇒ v.isEmpty || (v.matches("^[a-zA-Z0-9].*[a-zA-Z0-9]$") && v.length < 64)
    } map {
      case (k, v) ⇒ k → v.replaceAll("[^a-zA-Z0-9\\._-]", "_")
    }
    Map("labels" → l)
  }

  protected def labelSelector(labels: Map[String, String]): String = {
    labels.map { case (k, v) ⇒ s"$k=$v" } mkString ","
  }
}
