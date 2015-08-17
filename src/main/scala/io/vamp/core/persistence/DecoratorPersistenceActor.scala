package io.vamp.core.persistence

import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.PersistenceActor._

import scala.language.postfixOps

abstract class DecoratorPersistenceActor(target: ActorDescription) extends PersistenceActor {

  protected def info() = offload(actorFor(target) ? InfoRequest) match {
    case map: Map[_, _] => map.asInstanceOf[Map[Any, Any]] ++ infoMap()
    case other => other
  }

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int) = offload(actorFor(target) ? All(`type`, page, perPage)).asInstanceOf[ArtifactResponseEnvelope]

  protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean) = offload(actorFor(target) ? Create(artifact, source, ignoreIfExists)).asInstanceOf[Artifact]

  protected def read(name: String, `type`: Class[_ <: Artifact]) = offload(actorFor(target) ? Read(name, `type`)).asInstanceOf[Option[Artifact]]

  protected def update(artifact: Artifact, source: Option[String], create: Boolean) = offload(actorFor(target) ? Update(artifact, source, create)).asInstanceOf[Artifact]

  protected def delete(name: String, `type`: Class[_ <: Artifact]) = offload(actorFor(target) ? Delete(name, `type`)).asInstanceOf[Artifact]

  override protected def start() = actorFor(target) ! Start

  override protected def shutdown() = actorFor(target) ! Shutdown

  protected def infoMap(): Map[String, Any] = Map()
}