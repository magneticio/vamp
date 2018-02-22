package io.vamp.persistence

import java.io.{ File, FileWriter }

import akka.actor.Actor
import io.vamp.common.{ Artifact, ClassMapper, Config, ConfigMagnet }
import io.vamp.persistence.notification.CorruptedDataException

import scala.io.Source

class FilePersistenceActorMapper extends ClassMapper {
  val name = "file"
  val clazz: Class[_] = classOf[FilePersistenceActor]
}

object FilePersistenceActor {
  val directory: ConfigMagnet[String] = Config.string("vamp.persistence.file.directory")
}

class FilePersistenceActor extends InMemoryRepresentationPersistenceActor with PersistenceMarshaller with PersistenceDataReader with AccessGuard {

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
    case "load" ⇒ read()
  }: Actor.Receive) orElse super[InMemoryRepresentationPersistenceActor].receive

  override def preStart(): Unit = self ! "load"

  override protected def info(): Map[String, Any] = super.info() + ("type" → "file") + ("file" → file.getAbsolutePath)

  protected def read(): Unit = this.synchronized {
    try {
      for (line ← Source.fromFile(file).getLines()) {
        if (line.nonEmpty) readData(line)
      }
      removeGuard()
    }
    catch {
      case c: CorruptedDataException ⇒
        reportException(c)
        validData = false
    }
  }

  protected def set[T <: Artifact](artifact: T): T = {
    write(PersistenceRecord(artifact.name, artifact.kind, marshall(artifact)))
    setArtifact[T](artifact)
  }

  protected def delete[T <: Artifact](name: String, `type`: Class[T]): Boolean = super.get[T](name, `type`) match {
    case Some(_) ⇒
      write(PersistenceRecord(name, type2string(`type`)))
      deleteArtifact(name, type2string(`type`)).isDefined
    case _ ⇒ false
  }

  private def write(record: PersistenceRecord): Unit = {
    guard()
    val writer = new FileWriter(file, true)
    this.synchronized {
      try {
        writer.write(s"${marshallRecord(record)}\n")
        writer.flush()
      }
      finally writer.close()
    }
  }
}
