package io.vamp.persistence

import akka.actor.Actor
import io.vamp.common.Artifact
import io.vamp.common.http.OffsetEnvelope

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

trait InMemoryRepresentationPersistenceActor extends PersistenceActor with TypeOfArtifact {

  private val store: mutable.Map[String, mutable.Map[String, Artifact]] = new mutable.HashMap()

  override def receive = query orElse super[PersistenceActor].receive

  protected def query: Actor.Receive = PartialFunction.empty

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int) = Future.successful(allArtifacts(`type`, page, perPage))

  protected def get(name: String, `type`: Class[_ <: Artifact]) = Future.successful(readArtifact(name, `type`))

  protected def info(): Future[Map[String, Any]] = Future.successful(Map[String, Any](
    "artifacts" → (store.map {
      case (key, value) ⇒ key → Map[String, Any]("count" → value.values.size)
    } toMap)
  ))

  protected def allArtifacts(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
    log.debug(s"In memory representation: all [${`type`.getSimpleName}] of $page per $perPage")
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
    log.debug(s"In memory representation: read [${`type`.getSimpleName}] - $name}")
    store.get(type2string(`type`)).flatMap(_.get(name))
  }

  protected def setArtifact(artifact: Artifact): Artifact = {
    log.debug(s"In memory representation: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")
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
    log.debug(s"In memory representation: delete [${`type`}] - $name}")
    store.get(`type`) flatMap { map ⇒
      val artifact = map.remove(name)
      if (artifact.isEmpty) log.debug(s"Artifact not found for deletion: ${`type`}: $name")
      artifact
    }
  }
}