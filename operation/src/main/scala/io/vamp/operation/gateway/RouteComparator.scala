package io.vamp.operation.gateway

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC
import io.vamp.model.artifact.{ DefaultCondition, DefaultRoute, Gateway }
import io.vamp.model.event.Event
import io.vamp.pulse.PulseActor
import io.vamp.pulse.PulseActor.Publish

trait RouteComparator extends LazyLogging {

  // To get better data convert this to logger.info
  protected def logDebug(str: String = ""): Unit = {
    logger.info(str)
  }

  /**
    * This method gets old gateway and updated gateway, then
    * sends events depending on the changes to the routes
    *
    * @param gateway
    * @param updatedGateway
    */
  protected def compareNewRoutesAndGenerateEvents(gateway: Gateway, updatedGateway: Gateway, caller: String = "")(implicit actorSystem: ActorSystem, namespace: Namespace): Unit = {
    logDebug(s"RouteEvents Triggered on $caller")
    val currentRoutes = gateway.routes.map { case route: DefaultRoute ⇒ route.path.source → route }.toMap
    val nextRoutes = updatedGateway.routes.map { case route: DefaultRoute ⇒ route.path.source → route }.toMap

    val comparisonMap = for (key ← currentRoutes.keys ++ nextRoutes.keys)
      yield key → (currentRoutes.get(key), nextRoutes.get(key))

    comparisonMap.foreach {
      case (key: String, (Some(_), None)) ⇒
        sendRouteEvent(updatedGateway, "removed", key, caller)
      case (key: String, (None, Some(_))) ⇒
        sendRouteEvent(updatedGateway, "added", key, caller)
      case (key: String, (Some(currentRoute), Some(nextRoute))) ⇒ {
        logDebug(s"RouteEvents Route handling case for key: $key $caller")
        (currentRoute.condition, nextRoute.condition) match {
          case (Some(currentCondition: DefaultCondition), Some(nextCondition: DefaultCondition)) ⇒
            if (currentCondition.definition != nextCondition.definition)
              sendRouteEvent(updatedGateway, "conditionupdated", key, caller)
            else
              logDebug(s"RouteEvents Conditions didn't change for key: $key $caller")
          case (None, Some(_)) ⇒
            sendRouteEvent(updatedGateway, "conditionadded", key, caller)
          case (Some(_), None) ⇒
            sendRouteEvent(updatedGateway, "conditionremoved", key, caller)
          case (None, None) ⇒
            // condition didn't change
            logDebug(s"RouteEvents No Conditions for key: $key $caller")
          case _ ⇒
            logDebug(s"RouteEvents Condition Unhandled case: $key $caller")
        }

        (currentRoute.conditionStrength, nextRoute.conditionStrength) match {
          case (Some(currentConditionStrength), Some(nextConditionStrength)) ⇒
            if (currentConditionStrength.value != nextConditionStrength.value)
              sendRouteEvent(updatedGateway, "conditionstrengthupdated", key, caller)
            else
              logDebug(s"RouteEvents Condition Strength didn't change for key: $key $caller")
          case (None, Some(_)) ⇒
            sendRouteEvent(updatedGateway, "conditionstrengthnadded", key, caller)
          case (Some(_), None) ⇒
            sendRouteEvent(updatedGateway, "conditionstrengthremoved", key, caller)
          case (None, None) ⇒
            // condition strength didn't change
            logDebug(s"RouteEvents No Condition Strength for key: $key, $caller")
          case _ ⇒
            logDebug(s"RouteEvents Condition Strength Unhandled case: $key $caller")
        }

        (currentRoute.weight, nextRoute.weight) match {
          case (Some(currentWeight), Some(nextWeight)) ⇒
            if (currentWeight.value != nextWeight.value)
              sendRouteEvent(updatedGateway, "weightupdated", key, caller)
            else
              logDebug(s"RouteEvents Route Weight didn't change for key: $key $caller")
          case (None, Some(_)) ⇒
            sendRouteEvent(updatedGateway, "weightadded", key, caller)
          case (Some(_), None) ⇒
            sendRouteEvent(updatedGateway, "weightremoved", key, caller)
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

  protected def sendRouteEvent(gateway: Gateway, event: String, routeTag: String, caller: String = "")(implicit actorSystem: ActorSystem, namespace: Namespace): Unit = {
    logDebug(s"RouteEvents event: ${gateway.name} - $event $routeTag on $caller")
    val tags = Set(s"gateways${Event.tagDelimiter}${gateway.name}", s"route${Event.tagDelimiter}$event", s"routes${Event.tagDelimiter}$routeTag")
    IoC.actorFor[PulseActor] ! Publish(Event(Event.defaultVersion, tags, gateway))
  }

}
