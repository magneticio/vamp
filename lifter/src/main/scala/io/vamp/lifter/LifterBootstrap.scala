package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem, Props }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.lifter.kibana.KibanaDashboardInitializationActor
import io.vamp.lifter.persistence.ElasticsearchPersistenceInitializationActor
import io.vamp.lifter.pulse.PulseInitializationActor
import io.vamp.lifter.vga.{ VgaKubernetesSynchronizationActor, VgaKubernetesSynchronizationSchedulerActor, VgaMarathonSynchronizationActor, VgaMarathonSynchronizationSchedulerActor }
import io.vamp.persistence.PersistenceBootstrap

import scala.concurrent.duration._
import scala.language.postfixOps

object LifterBootstrap extends Bootstrap {

  val configuration = ConfigFactory.load().getConfig("vamp.lifter")

  val synchronizationMailbox = "vamp.lifter.vamp-gateway-agent.synchronization.mailbox"

  val vgaSynchronizationPeriod = configuration.getInt("vamp-gateway-agent.synchronization.period") seconds

  val vgaSynchronizationInitialDelay = configuration.getInt("vamp-gateway-agent.synchronization.initial-delay") seconds

  val vampGatewayAgentEnabled = configuration.getBoolean("vamp-gateway-agent.enabled")

  val pulseEnabled = configuration.getBoolean("pulse.enabled")

  val kibanaEnabled = configuration.getBoolean("kibana.enabled")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val persistence = if (configuration.getBoolean("persistence.enabled")) {
      PersistenceBootstrap.databaseType match {
        case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPersistenceInitializationActor] :: Nil
        case _               ⇒ Nil
      }
    } else Nil

    val vga = vgaSynchronization match {

      case "marathon" ⇒
        val actors = List(IoC.createActor(Props(classOf[VgaMarathonSynchronizationActor]).withMailbox(synchronizationMailbox)), IoC.createActor[VgaMarathonSynchronizationSchedulerActor])
        IoC.actorFor[VgaMarathonSynchronizationSchedulerActor] ! SchedulerActor.Period(vgaSynchronizationPeriod, vgaSynchronizationInitialDelay)
        actors

      case "kubernetes" ⇒
        val actors = List(IoC.createActor(Props(classOf[VgaKubernetesSynchronizationActor]).withMailbox(synchronizationMailbox)), IoC.createActor[VgaKubernetesSynchronizationSchedulerActor])
        IoC.actorFor[VgaKubernetesSynchronizationSchedulerActor] ! SchedulerActor.Period(vgaSynchronizationPeriod, vgaSynchronizationInitialDelay)
        actors

      case _ ⇒ Nil
    }

    val pulse = if (pulseEnabled)
      IoC.createActor[PulseInitializationActor] :: Nil
    else Nil

    val kibana = if (kibanaEnabled)
      IoC.createActor[KibanaDashboardInitializationActor] :: Nil
    else Nil

    persistence ++ vga ++ pulse ++ kibana
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {

    vgaSynchronization match {
      case "marathon"   ⇒ IoC.actorFor[VgaMarathonSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
      case "kubernetes" ⇒ IoC.actorFor[VgaKubernetesSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
      case _            ⇒
    }

    super.shutdown(actorSystem)
  }

  private def vgaSynchronization = if (vampGatewayAgentEnabled) ContainerDriverBootstrap.`type` else ""
}
