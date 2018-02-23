package io.vamp.persistence

import io.vamp.common.Artifact
import io.vamp.common.akka.CommonActorLogging
import io.vamp.common.http.OffsetEnvelope
import io.vamp.common.notification.NotificationProvider

import scala.collection.mutable
import scala.language.postfixOps

trait PersistenceRepresentation extends PersistenceApi with AccessGuard {
  this: CommonActorLogging with NotificationProvider ⇒

  private val store: mutable.Map[String, mutable.Map[String, Artifact]] = new mutable.HashMap()

  protected def info(): Map[String, Any] = Map[String, Any](
    "status" → (if (validData) "valid" else "corrupted"),
    "artifacts" → (store.map {
      case (key, value) ⇒ key → value.values.size
    } toMap)
  )

  protected def all(`type`: String): List[Artifact] = store.get(`type`).map(_.values.toList).getOrElse(Nil)

  protected def all[T <: Artifact](kind: String, page: Int, perPage: Int, filter: T ⇒ Boolean): ArtifactResponseEnvelope = {
    log.debug(s"In memory representation: all [$kind] of $page per $perPage")
    val artifacts = all(kind).filter { artifact ⇒ filter(artifact.asInstanceOf[T]) }
    val total = artifacts.size
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
    val (rp, rpp) = OffsetEnvelope.normalize(total, p, pp, ArtifactResponseEnvelope.maxPerPage)
    ArtifactResponseEnvelope(artifacts.slice((p - 1) * pp, p * pp), total, rp, rpp)
  }

  protected def get[T <: Artifact](name: String, kind: String): Option[T] = {
    log.debug(s"In memory representation: read [$kind] - $name}")
    store.get(kind).flatMap(_.get(name)).asInstanceOf[Option[T]]
  }

  protected def set[T <: Artifact](artifact: T, kind: String): T = {
    log.debug(s"In memory representation: set [$kind] - ${artifact.name}")
    store.get(kind) match {
      case None ⇒
        val map = new mutable.HashMap[String, Artifact]()
        map.put(artifact.name, artifact)
        store.put(kind, map)
      case Some(map) ⇒ map.put(artifact.name, artifact)
    }
    artifact
  }

  protected def delete[T <: Artifact](name: String, kind: String): Boolean = {
    log.debug(s"In memory representation: delete [$kind] - $name}")
    store.get(kind) flatMap { map ⇒
      val artifact = map.remove(name)
      if (artifact.isEmpty) log.debug(s"Artifact not found for deletion: $kind: $name")
      artifact
    } isDefined
  }
}
