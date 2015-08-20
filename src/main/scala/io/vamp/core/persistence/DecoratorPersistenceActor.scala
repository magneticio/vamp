package io.vamp.core.persistence

import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.PersistenceActor._

import scala.language.postfixOps

abstract class DecoratorPersistenceActor(target: ActorDescription) extends PersistenceActor {

  protected def info() = actorFor(target) ? InfoRequest map {
    case map: Map[_, _] => map.asInstanceOf[Map[Any, Any]] ++ infoMap()
    case other => other
  }

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int) = checked[ArtifactResponseEnvelope](actorFor(target) ? All(`type`, page, perPage))

  protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean) = checked[Artifact](actorFor(target) ? Create(artifact, source, ignoreIfExists))

  protected def read(name: String, `type`: Class[_ <: Artifact]) = checked[Option[Artifact]](actorFor(target) ? Read(name, `type`))

  protected def update(artifact: Artifact, source: Option[String], create: Boolean) = checked[Artifact](actorFor(target) ? Update(artifact, source, create))

  protected def delete(name: String, `type`: Class[_ <: Artifact]) = checked[Artifact](actorFor(target) ? Delete(name, `type`))

  override protected def start() = actorFor(target) ! Start

  override protected def shutdown() = actorFor(target) ! Shutdown

  protected def infoMap(): Map[String, Any] = Map()
}