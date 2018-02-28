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
  port:      PortTarget,
  groups:    Set[String]         = Set()
)

private case class PortTarget(name: Int, value: Int, index: Int)

object RouteSelectionProcessor {

  def targets(selector: RouteSelector, routingGroups: List[RoutingGroup], filter: Option[RouteSelector]): List[ExternalRouteTarget] = {
    execute(combine(selector, filter), flatten(routingGroups)).map(external).distinct
  }

  def groups(selector: RouteSelector, routingGroups: List[RoutingGroup], filter: Option[RouteSelector]): Map[String, List[ExternalRouteTarget]] = {
    execute(combine(selector, filter), flatten(routingGroups))
      .groupBy(target ⇒ target.groups.toList.map(g ⇒ s"($g)").sorted.mkString(","))
      .mapValues(list ⇒ list.map(external).distinct)
  }

  private def execute(node: AstNode, targets: List[GroupRouteTarget]): List[GroupRouteTarget] = {
    def filter(target: GroupRouteTarget, matches: Either[Boolean, String]): List[GroupRouteTarget] = matches match {
      case Left(b)  ⇒ if (b) target :: Nil else Nil
      case Right(g) ⇒ target.copy(groups = target.groups + g) :: Nil
    }

    node match {
      case s: NameSelector      ⇒ targets.flatMap(target ⇒ filter(target, s.matches(target.name)))
      case s: KindSelector      ⇒ targets.flatMap(target ⇒ filter(target, s.matches(target.kind)))
      case s: NamespaceSelector ⇒ targets.flatMap(target ⇒ filter(target, s.matches(target.namespace)))
      case s: ImageSelector     ⇒ targets.flatMap(target ⇒ if (target.image.isEmpty) Nil else filter(target, s.matches(target.image.get)))
      case s: LabelSelector ⇒
        targets.flatMap { target ⇒
          var nt: Option[GroupRouteTarget] = None
          target.labels.find {
            case (n, v) ⇒ filter(target, s.matches(n → v)) match {
              case t :: Nil ⇒
                nt = Option(t)
                true
              case _ ⇒ false
            }
          }
          nt.map(_ :: Nil).getOrElse(Nil)
        }
      case s: IpSelector        ⇒ targets.flatMap(target ⇒ filter(target, s.matches(target.ip)))
      case s: PortSelector      ⇒ targets.flatMap(target ⇒ filter(target, s.matches(target.port.name)))
      case s: PortIndexSelector ⇒ targets.flatMap(target ⇒ filter(target, s.matches(target.port.index)))

      case Or(op1, op2) ⇒
        val first = execute(op1, targets).map(target ⇒ target.id → target).toMap
        val second = execute(op2, targets).map { target ⇒
          first.get(target.id) match {
            case Some(t) ⇒ target.copy(groups = target.groups ++ t.groups)
            case None    ⇒ target
          }
        }.map(target ⇒ target.id → target).toMap
        second.values.toList ++ first.filterNot { case (n, _) ⇒ second.contains(n) }.values.toList

      case And(op1, op2) ⇒
        val first = execute(op1, targets).map(target ⇒ target.id → target).toMap
        execute(op2, targets).filter(target ⇒ first.contains(target.id)).map(target ⇒ target.copy(groups = target.groups ++ first(target.id).groups))

      case Negation(op) ⇒
        val is = execute(op, targets).map(target ⇒ target.id → target).toMap
        targets.filterNot(target ⇒ is.contains(target.id))

      case True ⇒ targets
      case _    ⇒ Nil
    }
  }

  private def combine(selector: RouteSelector, filter: Option[RouteSelector]): AstNode = filter.map(f ⇒ And(f.node, selector.node)).getOrElse(selector.node)

  private def flatten(routingGroups: List[RoutingGroup]): List[GroupRouteTarget] = {
    routingGroups.flatMap { group ⇒
      group.instances.flatMap { instance ⇒
        instance.ports.zipWithIndex.map {
          case (port, index) ⇒ GroupRouteTarget(
            s"${group.name}/${instance.ip}:${port.host}",
            group.name,
            group.kind,
            group.namespace,
            group.labels,
            group.image,
            instance.ip,
            PortTarget(port.host, port.container, index)
          )
        }
      }
    }
  }

  private def external(target: GroupRouteTarget) = ExternalRouteTarget(s"${target.ip}:${target.port.value}")
}
