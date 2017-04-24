package io.vamp.persistence

import akka.actor.Actor
import akka.util.Timeout
import io.vamp.common.{Config, ConfigMagnet}
import io.vamp.common.akka.SchedulerActor
import io.vamp.model.resolver.NamespaceValueResolver

import scala.concurrent.duration.FiniteDuration

object SearchSynchronizeActor {
  sealed trait SearchSynchronizeMessage
  case object Synchronize extends SearchSynchronizeMessage

  // Finite duration values from configuration
  val delay: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.search.delay")
  val synchronizationPeriod: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.search.synchronization.period")
  val timeout: ConfigMagnet[Timeout] = Config.timeout("vamp.persistence.response-timeout")
}

/**
  * Synchronizes a Database (e.g. Elasticsearch) for reading/searching
  */
trait SearchSynchronizeActor extends NamespaceValueResolver
    with SchedulerActor
    with PersistenceMarshaller {

  implicit lazy val timeout: Timeout = SearchSynchronizeActor.timeout()

  protected lazy val delay: FiniteDuration = SearchSynchronizeActor.delay()
  protected lazy val synchronization: FiniteDuration = SearchSynchronizeActor.synchronizationPeriod()

  override def receive: Receive = ({
    case SearchSynchronizeActor.Synchronize => synchronizeSearch(searchUrl, searchIndex)
  }: Actor.Receive) orElse super[SchedulerActor].receive

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(delay, self, SearchSynchronizeActor.Synchronize)
    self ! SchedulerActor.Period(synchronization, synchronization)
  }

  override def tick(): Unit = self ! SearchSynchronizeActor.Synchronize

  protected def synchronizeSearch(namespace: String, searchIndex: String): Unit

  protected def searchUrl: String

  protected def searchIndex: String

}
