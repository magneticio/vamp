package io.vamp.operation.gateway

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC
import io.vamp.model.artifact.{ DefaultCondition, DefaultRoute, Gateway, Route }
import io.vamp.model.event.Event
import io.vamp.pulse.PulseActor
import io.vamp.pulse.PulseActor.Publish

trait RouteComparator extends LazyLogging {

  protected def logDebug(str: String = ""): Unit = {
    logger.info(str)
  }

  /**
   * This method gets a gateway and new calculated routes, then
   * sends events depending on the changes to the routes
   * @param gateway
   * @param nextRoutesList
   */
  protected def compareNewRoutesAndGenerateEvents(gateway: Gateway, nextRoutesList: List[Route], caller: String = "")(implicit actorSystem: ActorSystem, namespace: Namespace): Unit = {
    logDebug(s"RouteEvents Triggered on $caller")
    val currentRoutes = gateway.routes.map { case route: DefaultRoute ⇒ route.path.source → route }.toMap
    val nextRoutes = nextRoutesList.map { case route: DefaultRoute ⇒ route.path.source → route }.toMap

    val comparisonMap = for (key ← currentRoutes.keys ++ nextRoutes.keys)
      yield key → (currentRoutes.get(key), nextRoutes.get(key))

    comparisonMap.foreach {
      case (key: String, (Some(_), None)) ⇒
        sendRouteEvent(gateway, "route:added", caller)
      case (key: String, (None, Some(_))) ⇒
        sendRouteEvent(gateway, "route:removed", caller)
      case (key: String, (Some(currentRoute), Some(nextRoute))) ⇒ {
        logDebug(s"RouteEvents Route handling case for key: $key $caller")
        (currentRoute.condition, nextRoute.condition) match {
          case (Some(currentCondition: DefaultCondition), Some(nextCondition: DefaultCondition)) ⇒
            if (currentCondition.definition != nextCondition.definition)
              sendRouteEvent(gateway, "route:conditionupdated", caller)
            else
              logDebug(s"RouteEvents Conditions didn't change for key: $key $caller")
          case (None, Some(_)) ⇒
            sendRouteEvent(gateway, "route:conditionadded", caller)
          case (Some(_), None) ⇒
            sendRouteEvent(gateway, "route:conditionremoved", caller)
          case (None, None) ⇒
            // condition didn't change
            logDebug(s"RouteEvents No Conditions for key: $key $caller")
          case _ ⇒
            logDebug(s"RouteEvents Condition Unhandled case: $key $caller")
        }

        (currentRoute.conditionStrength, nextRoute.conditionStrength) match {
          case (Some(currentConditionStrength), Some(nextConditionStrength)) ⇒
            if (currentConditionStrength.value != nextConditionStrength.value)
              sendRouteEvent(gateway, "route:conditionstrengthupdated", caller)
            else
              logDebug(s"RouteEvents Condition Strength didn't change for key: $key $caller")
          case (None, Some(_)) ⇒
            sendRouteEvent(gateway, "route:conditiostrengthnadded", caller)
          case (Some(_), None) ⇒
            sendRouteEvent(gateway, "route:conditionstrengthremoved", caller)
          case (None, None) ⇒
            // condition strength didn't change
            logDebug(s"RouteEvents No Condition Strength for key: $key, $caller")
          case _ ⇒
            logDebug(s"RouteEvents Condition Strength Unhandled case: $key $caller")
        }

        (currentRoute.weight, nextRoute.weight) match {
          case (Some(currentWeight), Some(nextWeight)) ⇒
            if (currentWeight.value != nextWeight.value)
              sendRouteEvent(gateway, "route:weightupdated", caller)
            else
              logDebug(s"RouteEvents Route Weight didn't change for key: $key $caller")
          case (None, Some(_)) ⇒
            sendRouteEvent(gateway, "route:weightadded", caller)
          case (Some(_), None) ⇒
            sendRouteEvent(gateway, "route:weightremoved", caller)
          case (None, None) ⇒
            // weight didn't change
            logDebug(s"RouteEvents No Route Weight for key: $key $caller")
          case _ ⇒
            logDebug(s"RouteEvents Route Weight Unhandled case: $key $caller")
        }
      }
      case _ ⇒ logDebug(s"RouteEvents Unhandled case for Route Pairs $caller")
    }
  }

  protected def sendRouteEvent(gateway: Gateway, event: String, caller: String = "")(implicit actorSystem: ActorSystem, namespace: Namespace): Unit = {
    logDebug(s"RouteEvents event: ${gateway.name} - $event  ${caller}")
    val tags = Set(s"gateways${Event.tagDelimiter}${gateway.name}", event)
    IoC.actorFor[PulseActor] ! Publish(Event(tags, gateway))
  }

}
