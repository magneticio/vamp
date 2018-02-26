package io.vamp.persistence

import akka.util.Timeout
import io.vamp.common.Artifact
import io.vamp.common.akka.CommonSupportForActors

import scala.concurrent.Future

trait PatchPersistenceOperations {
  this: PersistenceApi with PersistenceArchive with CommonSupportForActors ⇒

  implicit def timeout: Timeout

  protected def replyUpdate[T <: Artifact](artifact: T, update: Boolean): Unit = {
    if (update) reply(Future.successful(set[T](artifact))) else reply(Future.successful(artifact))
  }

  protected def replyUpdate[T <: Artifact](artifact: T, tag: String, source: String, update: Boolean): Unit = {
    if (update) reply(Future.successful {
      set[T](artifact)
      archiveUpdate(tag, source)
    })
    else reply(Future.successful(artifact))
  }

  protected def replyDelete[T <: Artifact](artifact: T, remove: Boolean): Unit = {
    if (remove) reply(Future.successful(delete[T](artifact))) else reply(Future.successful(true))
  }

  protected def replyDelete[T <: Artifact](name: String, `type`: Class[T], remove: Boolean): Unit = {
    if (remove) reply(Future.successful(delete[T](name, `type`))) else reply(Future.successful(true))
  }
}
