package io.vamp.lifter.vga

import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.lifter.notification.LifterNotificationProvider

import scala.collection.JavaConverters._

trait VgaSynchronizationActor extends CommonSupportForActors with LifterNotificationProvider {

  protected implicit val timeout = ContainerDriverActor.timeout

  protected val configuration = ConfigFactory.load().getConfig("vamp.lifter.vamp-gateway-agent.synchronization")

  protected val id = configuration.getString("id")

  protected val container = configuration.getString("container-image")
  protected val arguments = configuration.getStringList("container-arguments").asScala.toList

  protected val cpu = 0.1
  protected val mem = 128
}
