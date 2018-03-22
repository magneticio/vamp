package io.vamp.container_driver.marathon

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import io.vamp.common.http.HttpClient
import io.vamp.common.{ Config, Namespace }
import io.vamp.container_driver.ContainerInfo

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

private case class SharedMarathonClient(client: MarathonClient, counter: Int)

object MarathonClient {

  private val clients = new mutable.HashMap[MarathonClientConfig, SharedMarathonClient]()

  def acquire(config: MarathonClientConfig)(implicit system: ActorSystem, namespace: Namespace, executionContext: ExecutionContext, httpClient: HttpClient, logger: LoggingAdapter): MarathonClient = synchronized {
    logger.info(s"acquiring Marathon connection: ${config.marathonUrl}")
    val client = clients.get(config) match {
      case Some(shared) ⇒
        clients.put(config, shared.copy(counter = shared.counter + 1))
        shared.client
      case None ⇒
        logger.info(s"creating new Marathon connection: ${config.marathonUrl}")
        val shared = SharedMarathonClient(new MarathonClient(config), 1)
        clients.put(config, shared)
        shared.client
    }
    client.acquire()
    client
  }

  def release(config: MarathonClientConfig)(implicit namespace: Namespace, logger: LoggingAdapter): Unit = synchronized {
    logger.info(s"releasing Marathon connection: ${config.marathonUrl}")
    clients.get(config) match {
      case Some(shared) if shared.counter == 1 ⇒
        logger.info(s"closing Marathon connection: ${config.marathonUrl}")
        clients.remove(config)
        shared.client.close()
      case Some(shared) ⇒
        shared.client.release()
        clients.put(config, shared.copy(counter = shared.counter - 1))
      case None ⇒
    }
  }
}

class MarathonClient(val config: MarathonClientConfig) {

  private val infoUrl = s"${config.marathonUrl}/v2/info"
  private val appsUrl = s"${config.marathonUrl}/v2/apps"

  private val allAppCache = new MarathonAllAppCache
  private val caches = mutable.Map[Namespace, MarathonCache]()

  private val sseListeners = mutable.Map[Namespace, MarathonSse]()

  def info(implicit httpClient: HttpClient, executionContext: ExecutionContext): Future[ContainerInfo] = {
    def remove(key: String): Any ⇒ Any = {
      case m: Map[_, _] ⇒ m.asInstanceOf[Map[String, _]].filterNot { case (k, _) ⇒ k == key } map { case (k, v) ⇒ k → remove(key)(v) }
      case l: List[_]   ⇒ l.map(remove(key))
      case any          ⇒ any
    }

    val mesosUrl = s"${config.mesosUrl}/master/slaves"
    for {
      slaves ← httpClient.get[Any](s"$mesosUrl/slaves", config.headers)
      frameworks ← httpClient.get[Any](s"$mesosUrl/frameworks", config.headers)
      marathon ← httpClient.get[Any](s"$infoUrl", config.headers)
    } yield {
      val s: Any = slaves match {
        case s: Map[_, _] ⇒ s.asInstanceOf[Map[String, _]].getOrElse("slaves", Nil)
        case _            ⇒ Nil
      }
      val f = (remove("tasks") andThen remove("completed_tasks"))(frameworks)
      ContainerInfo("marathon", MarathonDriverInfo(MesosInfo(f, s), marathon))
    }
  }

  def get()(implicit httpClient: HttpClient, executionContext: ExecutionContext, logger: LoggingAdapter): Future[List[App]] = {
    allAppCache.all(appsUrl, config.headers)
  }

  def get(id: String)(implicit httpClient: HttpClient, namespace: Namespace, executionContext: ExecutionContext, logger: LoggingAdapter): Future[Option[App]] = {
    cache.read(id, () ⇒ get().map(_.find(app ⇒ app.id == id)))
  }

  def post(id: String, body: Any)(implicit httpClient: HttpClient, namespace: Namespace, executionContext: ExecutionContext, logger: LoggingAdapter): Future[Any] = {
    val operation = "create"
    cache.write(operation, id, () ⇒ {
      logger.info(s"marathon sending post request: $id")
      httpClient.post[Any](appsUrl, body, config.headers, logError = false).recover {
        case t if t.getMessage != null && t.getMessage.contains("already exists") ⇒ // ignore, sync issue
        case t ⇒
          logger.error(t.getMessage, t)
          cache.writeFailure(operation, id)
          Future.failed(t)
      }
    })
  }

  def put(id: String, body: Any)(implicit httpClient: HttpClient, namespace: Namespace, executionContext: ExecutionContext, logger: LoggingAdapter): Future[Any] = {
    val operation = "update"
    cache.write(operation, id, () ⇒ {
      logger.info(s"marathon sending put request: $id")
      httpClient.put[Any](s"$appsUrl/$id", body, config.headers).recover {
        case t ⇒
          logger.error(t.getMessage, t)
          cache.writeFailure(operation, id)
          Future.failed(t)
      }
    })
  }

  def delete(id: String)(implicit httpClient: HttpClient, namespace: Namespace, executionContext: ExecutionContext, logger: LoggingAdapter): Future[Any] = {
    val operation = "delete"
    cache.write(operation, id, () ⇒ {
      logger.info(s"marathon sending delete request: $id")
      httpClient.delete(s"$appsUrl/$id", config.headers, logError = false) recover {
        case _ ⇒
          cache.writeFailure(operation, id)
          None
      }
    })
  }

  def acquire()(implicit system: ActorSystem, namespace: Namespace, executionContext: ExecutionContext, httpClient: HttpClient, logger: LoggingAdapter): Unit = {
    caches.getOrElseUpdate(namespace, {
      val cc = MarathonCacheConfig()
      val cache = new MarathonCache(cc)
      if (Config.boolean(s"${MarathonDriverActor.marathonConfig}.sse")()) {
        val listener = MarathonSse(config, namespace, (kind, id) ⇒ kind match {
          case "deployment_success" ⇒
            allAppCache.invalidate
            if (cache.inCache(id)) cache.invalidate(id)
          case _ ⇒
        })
        listener.open()
        sseListeners.put(namespace, listener)
      }
      allAppCache.updateConfig(cc)
      cache
    })
  }

  def release()(implicit namespace: Namespace, logger: LoggingAdapter): Unit = {
    caches.get(namespace).foreach(_.close())
    sseListeners.get(namespace).foreach(_.close())
  }

  def close()(implicit logger: LoggingAdapter): Unit = {
    caches.values.foreach(_.close())
    sseListeners.values.foreach(_.close())
  }

  private def cache(implicit namespace: Namespace): MarathonCache = caches(namespace)
}
