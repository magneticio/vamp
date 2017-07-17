package io.vamp.persistence

import java.io.{ File, FileWriter }

import akka.actor.Actor
import io.vamp.common.{ Artifact, ClassMapper, Config, ConfigMagnet }
import io.vamp.persistence.CQRSActor.ReadAll
import org.json4s.DefaultFormats
import org.json4s.native.Serialization

import scala.concurrent.Future
import scala.io.Source

class FilePersistenceActorMapper extends ClassMapper {
  val name = "file"
  val clazz = classOf[FilePersistenceActor]
}

object FilePersistenceActor {
  val directory: ConfigMagnet[String] = Config.string("vamp.persistence.file.directory")
}

private object FileRecord {
  val set = "set"
  val delete = "delete"
}

private case class FileRecord(command: String, name: String, kind: String, artifact: Option[String])

class FilePersistenceActor extends InMemoryRepresentationPersistenceActor with PersistenceMarshaller {

  import FilePersistenceActor._

  private lazy val file = {
    val dir = directory()
    val file = new File(
      if (dir.endsWith(File.separator)) s"$dir${namespace.name}.txt" else s"$dir${File.separator}${namespace.name}.txt"
    )
    file.getParentFile.mkdirs()
    file.createNewFile()
    file
  }

  override def receive: Receive = ({
    case ReadAll ⇒ read()
  }: Actor.Receive) orElse super[InMemoryRepresentationPersistenceActor].receive

  override def preStart(): Unit = self ! ReadAll

  protected def info() = Future.successful(representationInfo() + ("type" → "file") + ("file" → file.getAbsolutePath))

  protected def read(): Unit = {
    for (line ← Source.fromFile(file).getLines()) {
      if (line.nonEmpty) {
        implicit val format = DefaultFormats
        val record = Serialization.read[FileRecord](line)
        if (record.command == FileRecord.set)
          record.artifact.foreach(content ⇒ unmarshall(record.kind, content).map(setArtifact))
        else if (record.command == FileRecord.delete)
          deleteArtifact(record.name, record.kind)
      }
    }
  }

  protected def set(artifact: Artifact) = Future {
    write(FileRecord(FileRecord.set, artifact.name, artifact.kind, Option(marshall(artifact))))
    setArtifact(artifact)
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]) = Future {
    write(FileRecord(FileRecord.delete, name, type2string(`type`), None))
    deleteArtifact(name, type2string(`type`)).isDefined
  }

  private def write(record: FileRecord): Unit = {
    val writer = new FileWriter(file, true)
    try {
      implicit val format = DefaultFormats
      writer.write(s"${Serialization.write(record)}\n")
      writer.flush()
    }
    finally writer.close()
  }
}
