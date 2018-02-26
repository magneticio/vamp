package io.vamp.persistence

import java.io.{ File, FileWriter }

import akka.actor.Actor
import io.vamp.common.notification.NotificationErrorException
import io.vamp.common.{ Artifact, ClassMapper, Config, ConfigMagnet }
import io.vamp.persistence.AccessGuard.LoadAll
import io.vamp.persistence.notification.{ CorruptedDataException, UnknownDataFormatException }

import scala.io.Source

class FilePersistenceActorMapper extends ClassMapper {
  val name = "file"
  val clazz: Class[_] = classOf[FilePersistenceActor]
}

object FilePersistenceActor {
  val directory: ConfigMagnet[String] = Config.string("vamp.persistence.file.directory")
}

class FilePersistenceActor
    extends PersistenceActor
    with PersistenceRepresentation
    with PersistenceMarshaller
    with PersistenceDataReader {

  import FilePersistenceActor._

  private lazy val file = {
    val dir = directory()
    val file = new File(
      if (dir.endsWith(File.separator)) s"$dir${namespace.name}.db" else s"$dir${File.separator}${namespace.name}.db"
    )
    file.getParentFile.mkdirs()
    file.createNewFile()
    file
  }

  override def receive: Receive = ({
    case LoadAll ⇒ read()
  }: Actor.Receive) orElse super.receive

  override def preStart(): Unit = self ! LoadAll

  override protected def info(): Map[String, Any] = super.info() + ("type" → "file") + ("file" → file.getAbsolutePath)

  protected def read(): Unit = this.synchronized {
    for (line ← Source.fromFile(file).getLines()) {
      if (line.nonEmpty) try dataRead(line) catch {
        case NotificationErrorException(_: UnknownDataFormatException, _) ⇒ // already logged, skip to the next line
        case c: CorruptedDataException ⇒
          reportException(c)
          validData = false
          throw c
      }
    }
    removeGuard()
  }

  override protected def set[T <: Artifact](artifact: T, kind: String): T = {
    def store(): T = {
      write(PersistenceRecord(artifact.name, artifact.kind, marshall(artifact)))
      super.set[T](artifact, kind)
    }

    super.get[T](artifact.name, kind) match {
      case Some(a) if a != artifact ⇒ store()
      case Some(_)                  ⇒ artifact
      case None                     ⇒ store()
    }
  }

  override protected def delete[T <: Artifact](name: String, kind: String): Boolean = {
    super.get[T](name, kind) match {
      case Some(_) ⇒
        write(PersistenceRecord(name, kind))
        super.delete(name, kind)
      case _ ⇒ false
    }
  }

  override protected def dataSet(artifact: Artifact, kind: String): Artifact = super.set(artifact, kind)

  override protected def dataDelete(name: String, kind: String): Unit = super.delete(name, kind)

  private def write(record: PersistenceRecord): Unit = {
    guard()
    val writer = new FileWriter(file, true)
    try {
      writer.write(s"${marshallRecord(record)}\n")
      writer.flush()
    }
    finally writer.close()
  }
}
