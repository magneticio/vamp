package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem, Props }
import io.vamp.common.akka.{ ActorBootstrap, IoC, SchedulerActor }
import io.vamp.common.config.Config
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.kibana.KibanaDashboardInitializationActor
import io.vamp.lifter.persistence.ElasticsearchPersistenceInitializationActor
import io.vamp.lifter.pulse.PulseInitializationActor
import io.vamp.lifter.vga._
import io.vamp.persistence.PersistenceBootstrap

import scala.concurrent.duration._
import scala.language.postfixOps

object LifterBootstrap extends ActorBootstrap {

  val config = Config.config("vamp.lifter")

  val synchronizationMailbox = "vamp.lifter.vamp-gateway-agent.synchronization.mailbox"

  val vgaSynchronizationPeriod = config.duration("vamp-gateway-agent.synchronization.period")

  val vgaSynchronizationInitialDelay = config.duration("vamp-gateway-agent.synchronization.initial-delay")

  val vampGatewayAgentEnabled = config.boolean("vamp-gateway-agent.enabled")

  val pulseEnabled = config.boolean("pulse.enabled")

  val kibanaEnabled = config.boolean("kibana.enabled")

  val artifactEnabled = config.boolean("artifact.enabled")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val persistence = if (config.boolean("persistence.enabled")) {
      PersistenceBootstrap.databaseType match {
        case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPersistenceInitializationActor] :: Nil
        case _               ⇒ Nil
      }
    } else Nil

    val vga = vgaSynchronization match {

      case "rancher" ⇒
        val actors = List(IoC.createActor(Props(classOf[VgaRancherSynchronizationActor]).withMailbox(synchronizationMailbox)), IoC.createActor[VgaRancherSynchronizationSchedulerActor])
        IoC.actorFor[VgaRancherSynchronizationSchedulerActor] ! SchedulerActor.Period(vgaSynchronizationPeriod, vgaSynchronizationInitialDelay)
        actors

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

    val artifact = if (artifactEnabled)
      IoC.createActor[ArtifactInitializationActor] :: Nil
    else Nil

    persistence ++ vga ++ pulse ++ kibana ++ artifact
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {

    vgaSynchronization match {
      case "rancher"    ⇒ IoC.actorFor[VgaRancherSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
      case "marathon"   ⇒ IoC.actorFor[VgaMarathonSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
      case "kubernetes" ⇒ IoC.actorFor[VgaKubernetesSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
      case _            ⇒
    }

    super.shutdown(actorSystem)
  }

  private def vgaSynchronization = if (vampGatewayAgentEnabled) ContainerDriverBootstrap.`type` else ""
}
