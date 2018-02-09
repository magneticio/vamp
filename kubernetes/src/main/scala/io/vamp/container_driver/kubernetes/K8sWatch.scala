package io.vamp.container_driver.kubernetes

import akka.actor.ActorSystem
import com.google.gson.reflect.TypeToken
import com.squareup.okhttp.Call
import com.typesafe.scalalogging.Logger
import io.kubernetes.client.models._
import io.kubernetes.client.util.Watch
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class K8sWatch(client: K8sClient)(implicit system: ActorSystem) {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private implicit val ec: ExecutionContext = system.dispatcher

  private var running = true

  private val retryDelay = 5 seconds
  private val initialDelay = 1 seconds

  logger.info(s"starting Kubernetes watch: ${client.config.url}")

  private val watchHandles = new mutable.HashMap[String, Call]()

  watch(
    K8sCache.job,
    () ⇒ client.batchV1Api.listNamespacedJobCall(client.config.namespace, null, null, null, null, 0, true, null, null),
    (call: Call) ⇒ Watch.createWatch(client.batchV1Api.getApiClient, call, new TypeToken[Watch.Response[V1Job]]() {}.getType)
  )

  watch(
    K8sCache.pod,
    () ⇒ client.coreV1Api.listNamespacedPodCall(client.config.namespace, null, null, null, null, 0, true, null, null),
    (call: Call) ⇒ Watch.createWatch(client.coreV1Api.getApiClient, call, new TypeToken[Watch.Response[V1Pod]]() {}.getType)
  )

  watch(
    K8sCache.service,
    () ⇒ client.coreV1Api.listNamespacedServiceCall(client.config.namespace, null, null, null, null, 0, true, null, null),
    (call: Call) ⇒ Watch.createWatch(client.coreV1Api.getApiClient, call, new TypeToken[Watch.Response[V1Service]]() {}.getType)
  )

  watch(
    K8sCache.namespace,
    () ⇒ client.coreV1Api.listNamespaceCall(null, null, null, null, 0, true, null, null),
    (call: Call) ⇒ Watch.createWatch(client.coreV1Api.getApiClient, call, new TypeToken[Watch.Response[V1Namespace]]() {}.getType)
  )

  watch(
    K8sCache.daemonSet,
    () ⇒ client.extensionsV1beta1Api.listNamespacedDaemonSetCall(client.config.namespace, null, null, null, null, 0, true, null, null),
    (call: Call) ⇒ Watch.createWatch(client.extensionsV1beta1Api.getApiClient, call, new TypeToken[Watch.Response[V1beta1DaemonSet]]() {}.getType)
  )

  watch(
    K8sCache.deployment,
    () ⇒ client.extensionsV1beta1Api.listNamespacedDeploymentCall(client.config.namespace, null, null, null, null, 0, true, null, null),
    (call: Call) ⇒ Watch.createWatch(client.extensionsV1beta1Api.getApiClient, call, new TypeToken[Watch.Response[ExtensionsV1beta1Deployment]]() {}.getType)
  )

  watch(
    K8sCache.replicaSet,
    () ⇒ client.extensionsV1beta1Api.listNamespacedReplicaSetCall(client.config.namespace, null, null, null, null, 0, true, null, null),
    (call: Call) ⇒ Watch.createWatch(client.extensionsV1beta1Api.getApiClient, call, new TypeToken[Watch.Response[V1beta1ReplicaSet]]() {}.getType)
  )

  def close(): Unit = {
    logger.info(s"closing Kubernetes watch: ${client.config.url}")
    running = false
    watchHandles.values.foreach(_.cancel())
  }

  private def watch(kind: String, call: () ⇒ Call, watch: (Call) ⇒ Watch[AnyRef]): Unit = {

    def stream(): Unit = {
      try {
        logger.info(s"watching [$kind]: ${client.config.url}")
        val c = call()
        watchHandles.put(kind, c)
        watch(c).iterator().asScala.foreach(handleEvent)
      }
      catch {
        case e: Exception ⇒
          if (running) {
            logger.error(s"ERROR: watch $kind: ${e.getMessage}")
            system.scheduler.scheduleOnce(retryDelay, () ⇒ stream())
          }
      }
    }

    system.scheduler.scheduleOnce(initialDelay, () ⇒ stream())
  }

  private def handleEvent(event: AnyRef): Unit = event match {
    case j: V1Job                       ⇒ client.cache.invalidate(K8sCache.job, j.getMetadata.getName)
    case p: V1Pod                       ⇒ client.cache.invalidate(K8sCache.pod, p.getMetadata.getName)
    case s: V1Service                   ⇒ client.cache.invalidate(K8sCache.service, s.getMetadata.getName)
    case n: V1Namespace                 ⇒ client.cache.invalidate(K8sCache.namespace, n.getMetadata.getName)
    case d: V1beta1DaemonSet            ⇒ client.cache.invalidate(K8sCache.daemonSet, d.getMetadata.getName)
    case d: ExtensionsV1beta1Deployment ⇒ client.cache.invalidate(K8sCache.deployment, d.getMetadata.getName)
    case r: V1beta1ReplicaSet           ⇒ client.cache.invalidate(K8sCache.replicaSet, r.getMetadata.getName)
    case _                              ⇒
  }
}
