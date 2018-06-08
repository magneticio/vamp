package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.{ IoC, ReplyCheck }
import io.vamp.common.http.{ OffsetEnvelope, OffsetResponseEnvelope }
import io.vamp.container_driver.ContainerDriverActor.{ GetNodes, GetRoutingGroups }
import io.vamp.container_driver.{ ContainerDriverActor, RoutingGroup, SchedulerNode }
import io.vamp.model.artifact.RouteSelector
import io.vamp.model.notification.InvalidSelectorError
import io.vamp.operation.gateway.{ GatewaySelectorResolver, RouteSelectionProcessor }
import io.vamp.persistence.ArtifactResponseEnvelope

import scala.concurrent.Future
import scala.util.Try

trait SchedulerController extends ReplyCheck with GatewaySelectorResolver with AbstractController {

  def nodes(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[OffsetResponseEnvelope[SchedulerNode]] = {
    checked[List[SchedulerNode]](IoC.actorFor[ContainerDriverActor] ? GetNodes) map paginate(page, perPage)
  }

  def routing(selector: Option[String])(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[OffsetResponseEnvelope[RoutingGroup]] = {
    val filter = selector.map(s ⇒ Try(RouteSelector(s).verified).getOrElse(throwException(InvalidSelectorError(selector.get))))
    checked[List[RoutingGroup]](IoC.actorFor[ContainerDriverActor] ? GetRoutingGroups).map { routingGroups ⇒
      val targets = RouteSelectionProcessor.targets(
        Try(RouteSelector(defaultSelector()).verified).getOrElse(RouteSelector("false")), routingGroups, filter
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

      paginate(page, perPage)(all)
    }
  }

  private def paginate[T](page: Int, perPage: Int)(items: List[T]): OffsetResponseEnvelope[T] = {
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
    val (rp, rpp) = OffsetEnvelope.normalize(items.size, p, pp, ArtifactResponseEnvelope.maxPerPage)

    new OffsetResponseEnvelope[T] {
      val page: Int = rp
      val perPage: Int = rpp
      val total: Long = items.size
      val response: List[T] = items.slice((p - 1) * pp, p * pp)
    }
  }
}
