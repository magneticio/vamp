package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.{ IoC, ReplyCheck }
import io.vamp.container_driver.ContainerDriverActor.GetRoutingGroups
import io.vamp.container_driver.{ ContainerDriverActor, RoutingGroup }

import scala.concurrent.Future

trait SchedulerController extends ReplyCheck with AbstractController {

  def routing(implicit namespace: Namespace, timeout: Timeout): Future[List[RoutingGroup]] = {
    checked[List[RoutingGroup]](IoC.actorFor[ContainerDriverActor] ? GetRoutingGroups)
  }
}
