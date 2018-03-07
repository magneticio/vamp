package io.vamp.container_driver.marathon

import akka.event.LoggingAdapter
import io.vamp.common.CacheStore
import io.vamp.common.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

class MarathonAllAppCache {

  private val key = "apps"
  private lazy val cache = new CacheStore()
  private var config: MarathonCacheConfig = _

  def all(url: String, headers: List[(String, String)])(implicit httpClient: HttpClient, executionContext: ExecutionContext, logger: LoggingAdapter): Future[List[App]] = synchronized {
    cache.get[Future[List[App]]](key) match {
      case Some(result) ⇒
        logger.debug(s"cache get all apps")
        result.asInstanceOf[Future[List[App]]]
      case None ⇒
        logger.info(s"marathon sending get all request")
        val future = {
          httpClient
            .get[AppsResponse](s"$url?embed=apps.tasks&embed=apps.taskStats", headers, logError = false)
            .recover {
              case t: Throwable ⇒
                logger.error(s"Error while getting apps ⇒ ${t.getMessage}", t)
                cache.get[Future[List[App]]](key).foreach { value ⇒
                  cache.put[Future[List[App]]](key, value, config.failureTimeToLivePeriod)
                }
                AppsResponse(Nil)
            }.map(_.apps)
        }
        cache.put[Future[List[App]]](key, future, config.readTimeToLivePeriod)
        future
    }
  }

  def invalidate(implicit logger: LoggingAdapter): Unit = synchronized {
    logger.info(s"cache remove all apps")
    cache.remove(key)
  }

  def updateConfig(config: MarathonCacheConfig): Unit = {
    if (this.config == null) this.config = config
    else {
      if (this.config.readTimeToLivePeriod > config.readTimeToLivePeriod)
        this.config.copy(readTimeToLivePeriod = config.readTimeToLivePeriod)

      if (this.config.failureTimeToLivePeriod > config.failureTimeToLivePeriod)
        this.config.copy(failureTimeToLivePeriod = config.failureTimeToLivePeriod)
    }
  }
}
