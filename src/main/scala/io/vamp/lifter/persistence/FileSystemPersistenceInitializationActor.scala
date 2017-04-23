package io.vamp.lifter.persistence

import java.io.File

import io.vamp.common.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.resolver.NamespaceValueResolver

/**
 * Initializes the FileSystemPersistenceActor including the creation of the csv file
 */
class FileSystemPersistenceInitializationActor extends CommonSupportForActors
    with NamespaceValueResolver
    with LifterNotificationProvider {

  override def receive: Receive = {
    case "init" ⇒
      val filePath: String = Config.string("vamp.persistence.database.filesystem.path")()
      val file = new File(filePath)

      if (!file.exists()) {
        if (file.createNewFile()) println(s"${getClass.getSimpleName}: Created file: $filePath")
        else {
          println(s"${getClass.getSimpleName}: Can not create file: $filePath")
          self ! "init"
        }
      } else println(s"${getClass.getSimpleName}: Found existing file: $filePath")
  }

  override def preStart(): Unit = {
    println("PRE START CALLED!!!")
    self ! "init"
  }

}
