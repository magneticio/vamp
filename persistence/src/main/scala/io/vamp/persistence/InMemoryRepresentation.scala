package io.vamp.persistence

import akka.event.LoggingAdapter
import io.vamp.common.Artifact
import io.vamp.common.http.OffsetEnvelope
import io.vamp.persistence.notification.PersistenceNotificationProvider

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

trait InMemoryRepresentationPersistenceActor
    extends PersistenceActor
    with TypeOfArtifact
    with PersistenceNotificationProvider {

  protected implicit val loggingAdapter: LoggingAdapter = log

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope] =
    Future.successful(allArtifacts(`type`, page, perPage))

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]] =
    Future.successful(readArtifact(name, `type`))

  private val store: mutable.Map[String, mutable.Map[String, Artifact]] = new mutable.HashMap()

  protected def representationInfo() = Map[String, Any](
    "artifacts" → (store.map {
      case (key, value) ⇒ key → Map[String, Any]("count" → value.values.size)
    } toMap)
  )

  protected def allArtifacts(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
    loggingAdapter.debug(s"In memory representation: all [${`type`.getSimpleName}] of $page per $perPage")
    val artifacts = store.get(type2string(`type`)) match {
      case None      ⇒ Nil
      case Some(map) ⇒ map.values.toList
    }
    val total = artifacts.size
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
    val (rp, rpp) = OffsetEnvelope.normalize(total, p, pp, ArtifactResponseEnvelope.maxPerPage)
    ArtifactResponseEnvelope(artifacts.slice((p - 1) * pp, p * pp), total, rp, rpp)
  }

  protected def readArtifact(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = {
    loggingAdapter.debug(s"In memory representation: read [${`type`.getSimpleName}] - $name}")
    store.get(type2string(`type`)).flatMap(_.get(name))
  }

  protected def setArtifact(artifact: Artifact): Artifact = {
    loggingAdapter.debug(s"In memory representation: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")
    store.get(type2string(artifact.getClass)) match {
      case None ⇒
        val map = new mutable.HashMap[String, Artifact]()
        map.put(artifact.name, artifact)
        store.put(type2string(artifact.getClass), map)
      case Some(map) ⇒ map.put(artifact.name, artifact)
    }
    artifact
  }

  protected def deleteArtifact(name: String, `type`: String): Option[Artifact] = {
    loggingAdapter.debug(s"In memory representation: delete [${`type`}] - $name}")
    store.get(`type`) flatMap { map ⇒
      val artifact = map.remove(name)
      if (artifact.isEmpty) loggingAdapter.debug(s"Artifact not found for deletion: ${`type`}: $name")
      artifact
    }
  }

}