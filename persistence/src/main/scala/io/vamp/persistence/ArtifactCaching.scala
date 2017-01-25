package io.vamp.persistence

import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.Artifact

import scala.concurrent.Future

trait ArtifactCaching extends TypeOfArtifact {
  this: NotificationProvider ⇒

  private val cache = new ConcurrentHashMap[String, SoftReference[Future[_]]]()

  protected def cacheAll(enabled: Boolean)(`type`: Class[_ <: Artifact], page: Int, perPage: Int)(otherwise: () ⇒ Future[ArtifactResponseEnvelope]): Future[ArtifactResponseEnvelope] = {
    if (enabled) retrieve(cacheKey(`type`, "*", page, perPage), otherwise) else otherwise()
  }

  protected def cacheGet(enabled: Boolean)(name: String, `type`: Class[_ <: Artifact])(otherwise: () ⇒ Future[Option[Artifact]]): Future[Option[Artifact]] = {
    if (enabled) retrieve(cacheKey(`type`, name), otherwise) else otherwise()
  }

  protected def cacheSet(enabled: Boolean)(artifact: Artifact)(otherwise: () ⇒ Future[Artifact]): Future[Artifact] = {
    if (enabled) cache.clear()
    otherwise()
  }

  protected def cacheDelete(enabled: Boolean)(name: String, `type`: Class[_ <: Artifact])(otherwise: () ⇒ Future[Boolean]): Future[Boolean] = {
    if (enabled) cache.clear()
    otherwise()
  }

  private def retrieve[T](key: String, otherwise: () ⇒ Future[T]): Future[T] = {
    Option(cache.get(key)).flatMap(value ⇒ Option(value.get())).getOrElse {
      val future = otherwise()
      cache.put(key, new SoftReference(future))
      future
    }.asInstanceOf[Future[T]]
  }

  private def cacheKey(`type`: String, name: String, page: Int = 1, perPage: Int = 1): String = s"${`type`}/$name/$page/$perPage"
}
