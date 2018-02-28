package io.vamp.operation.gateway

import io.vamp.container_driver.RoutingGroup
import io.vamp.model.artifact.{ ExternalRouteTarget, RouteSelector }
import io.vamp.model.parser._

private case class GroupRouteTarget(
  id:        String,
  name:      String,
  kind:      String,
  namespace: String,
  labels:    Map[String, String],
  image:     Option[String],
  ip:        String,
  port:      PortTarget
)

private case class PortTarget(name: Int, value: Int, index: Int)

object RouteSelectionProcessor {

  def execute(selector: RouteSelector, routingGroups: List[RoutingGroup], byGroup: Boolean = false): List[ExternalRouteTarget] = {
    val targets = routingGroups.flatMap { group ⇒
      group.instances.flatMap { instance ⇒
        instance.ports.zipWithIndex.map {
          case (port, index) ⇒ GroupRouteTarget(s"${group.name}/${instance.ip}:${port.host}", group.name, group.kind, group.namespace, group.labels, group.image, instance.ip, PortTarget(port.host, port.container, index))
        }
      }
    }
    execute(selector.node, targets).map(target ⇒ ExternalRouteTarget(s"${target.ip}:${target.port.value}")).distinct
  }

  private def execute(node: AstNode, targets: List[GroupRouteTarget]): List[GroupRouteTarget] = node match {
    case s: NameSelector      ⇒ targets.filter(target ⇒ s.matches(target.name))
    case s: KindSelector      ⇒ targets.filter(target ⇒ s.matches(target.kind))
    case s: NamespaceSelector ⇒ targets.filter(target ⇒ s.matches(target.namespace))
    case s: ImageSelector     ⇒ targets.filter(target ⇒ target.image.isDefined && s.matches(target.image.get))
    case s: LabelSelector     ⇒ targets.filter(_.labels.exists { case (n, v) ⇒ s.matches(n → v) })
    case s: IpSelector        ⇒ targets.filter(target ⇒ s.matches(target.ip))
    case s: PortSelector      ⇒ targets.filter(target ⇒ s.port == target.port.name)
    case s: PortIndexSelector ⇒ targets.filter(target ⇒ s.index == target.port.index)
    case Or(op1, op2)         ⇒ execute(op1, targets) ++ execute(op2, targets)
    case And(op1, op2) ⇒
      val first = execute(op1, targets).map(target ⇒ target.id → target).toMap
      execute(op2, targets).filter(target ⇒ first.contains(target.id))
    case Negation(op) ⇒
      val is = execute(op, targets).map(target ⇒ target.id → target).toMap
      targets.filterNot(target ⇒ is.contains(target.id))
    case True ⇒ targets
    case _    ⇒ Nil
  }
}
