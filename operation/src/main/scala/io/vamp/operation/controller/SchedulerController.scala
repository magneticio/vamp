package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.{ IoC, ReplyCheck }
import io.vamp.common.http.{ OffsetEnvelope, OffsetResponseEnvelope }
import io.vamp.container_driver.ContainerDriverActor.GetRoutingGroups
import io.vamp.container_driver.{ ContainerDriverActor, RoutingGroup }
import io.vamp.model.artifact.RouteSelector
import io.vamp.model.notification.InvalidSelectorError
import io.vamp.operation.gateway.{ GatewaySynchronizationActor, RouteSelectionProcessor }
import io.vamp.persistence.ArtifactResponseEnvelope

import scala.concurrent.Future
import scala.util.Try

trait SchedulerController extends ReplyCheck with AbstractController {

  def routing(selector: Option[String])(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[OffsetResponseEnvelope[RoutingGroup]] = {

    val filter = selector.map(s ⇒ Try(RouteSelector(s)).getOrElse(throwException(InvalidSelectorError(selector.get))))

    checked[List[RoutingGroup]](IoC.actorFor[ContainerDriverActor] ? GetRoutingGroups).map { routingGroups ⇒
      val targets = RouteSelectionProcessor.targets(
        Try(RouteSelector(GatewaySynchronizationActor.selector()).verified).getOrElse(RouteSelector("false")), routingGroups, filter
      ).map(_.url).toSet

      val all = routingGroups.flatMap { group ⇒
        val instances = group.instances.flatMap { instance ⇒
          val ports = instance.ports.flatMap { port ⇒
            if (targets.contains(s"${instance.ip}:${port.container}")) port :: Nil else Nil
          }
          if (ports.nonEmpty) instance.copy(ports = ports) :: Nil else Nil
        }
        if (instances.nonEmpty) group.copy(instances = instances) :: Nil else Nil
      }

      val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
      val (rp, rpp) = OffsetEnvelope.normalize(all.size, p, pp, ArtifactResponseEnvelope.maxPerPage)

      new OffsetResponseEnvelope[RoutingGroup] {
        val page: Int = rp
        val perPage: Int = rpp
        val total: Long = all.size
        val response: List[RoutingGroup] = all.slice((p - 1) * pp, p * pp)
      }
    }
  }
}
