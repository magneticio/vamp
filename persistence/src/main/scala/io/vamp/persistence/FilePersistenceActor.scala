package io.vamp.persistence

import java.io.{ File, FileWriter, PrintWriter }

import io.vamp.common.{ ClassMapper, Config, ConfigMagnet }
import io.vamp.model.Model
import io.vamp.persistence.notification.PersistenceOperationFailure

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.io.{ BufferedSource, Source }
import scala.util.Try

object FilePersistenceActor {

  val filePath: ConfigMagnet[String] = Config.string("vamp.persistence.database.filesystem.path")
  val delayPeriod: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.database.filesystem.delay")
  val synchronizationPeriod: ConfigMagnet[FiniteDuration] =
    Config.duration("vamp.persistence.database.filesystem.synchronization.period")

}

class FilePersistenceActorMapper extends ClassMapper {
  val name: String = "filesystem"
  val clazz: Class[FilePersistenceActor] = classOf[FilePersistenceActor]
}

/**
 * CQRS actor for the filesystem
 */
class FilePersistenceActor extends CQRSActor {

  override protected lazy val delay: FiniteDuration = FilePersistenceActor.delayPeriod()

  override protected lazy val synchronization: FiniteDuration = FilePersistenceActor.synchronizationPeriod()

  lazy val filePath: String = FilePersistenceActor.filePath()

  lazy val file: File = new File(filePath)

  override protected def read(): Long =
    Try {
      val bufferedSource: BufferedSource = Source.fromFile(file)

      bufferedSource
        .getLines()
        .foreach(_.split(":~:~:") match {
          case Array(time, version, command, dType, name, definition) ⇒
            val timeMillis = time.toLong

            if (timeMillis > getLastId) {
              setArtifact(unmarshall(dType, definition).get)
              setLastId(timeMillis)
            }
          case Array(time, version, command, dType, name) ⇒
            val timeMillis = time.toLong

            if (timeMillis > getLastId) {
              deleteArtifact(dType, name)
              setLastId(timeMillis)
            }
        })

      bufferedSource.close()

      getLastId
    }.get

  override protected def insert(name: String, kind: String, content: Option[String]): Try[Option[Long]] = {
    Try {
      val timeMillis = System.currentTimeMillis()
      setLastId(timeMillis)

      val row: String = content.map { definition ⇒
        s"$timeMillis:~:~:${Model.version}:~:~:$commandSet:~:~:$kind:~:~:$name:~:~:$definition"
      }.getOrElse(s"$timeMillis:~:~:${Model.version}:~:~:$commandDelete:~:~:$kind:~:~:$name")

      appendRow(file, row)

      Some(getLastId)
    }
  }

  def appendRow(file: File, row: String): Unit = {
    val fr = new FileWriter(file, true)
    val pr = new PrintWriter(fr)
    try {
      pr.println(row)
    }
    catch {
      case e: Exception ⇒ throwException(PersistenceOperationFailure(e))
    }
    finally {
      pr.close()
      fr.close()
    }
  }

  override protected def info(): Future[Any] =
    Future.successful(representationInfo() + ("type" → "filesystem") + ("filePath" → filePath))

}
