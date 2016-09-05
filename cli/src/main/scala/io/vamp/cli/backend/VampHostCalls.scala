package io.vamp.cli.backend

import java.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods
import akka.util.Timeout
import io.vamp.cli.commandline.CommandLineBasics
import io.vamp.cli.commands.IoUtils
import io.vamp.common.http.{ RestApiContentTypes, RestApiMarshaller, RestClient, RestClientException }
import io.vamp.model.artifact._
import io.vamp.model.reader._
import org.json4s.native._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object VampHostCalls extends RestSupport with RestApiMarshaller with RestApiContentTypes with CommandLineBasics with IoUtils {

  val system = ActorSystem()

  val timeout = Timeout(30 seconds)

  def getDeploymentAsBlueprint(deploymentId: String)(implicit vampHost: String): Option[Blueprint] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/deployments/$deploymentId?as_blueprint=true").map(BlueprintReader.read(_))

  def createBreed(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Breed] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/breeds", Some(definition)) match {
      case Some(b) ⇒ Some(BreedReader.read(b))
      case _       ⇒ terminateWithError("Breed not created")
    }
  }

  def createBlueprint(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Blueprint] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/blueprints", Some(definition)) match {
      case Some(b) ⇒ Some(BlueprintReader.read(b))
      case _       ⇒ terminateWithError("Blueprint not created")
    }
  }

  def createGateway(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Gateway] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/gateways", Some(definition)) match {
      case Some(gateway) ⇒ Some(GatewayReader.read(gateway))
      case _             ⇒ terminateWithError("Gateway not created")
    }
  }

  def createEscalation(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Escalation] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/escalations", Some(definition)) match {
      case Some(escalation) ⇒ Some(EscalationReader.read(escalation))
      case _                ⇒ terminateWithError("Escalation not created")
    }
  }

  def createCondition(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Condition] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/conditions", Some(definition)) match {
      case Some(condition) ⇒ Some(ConditionReader.read(condition))
      case _               ⇒ terminateWithError("Condition not created")
    }
  }

  def createRewrite(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Rewrite] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/rewrites", Some(definition)) match {
      case Some(rewrite) ⇒ Some(RewriteReader.read(rewrite))
      case _             ⇒ terminateWithError("Rewrite not created")
    }
  }

  def createRoute(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Route] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/routings", Some(definition)) match {
      case Some(routing) ⇒ Some(RouteReader.read(routing))
      case _             ⇒ terminateWithError("Routing not created")
    }
  }

  def createScale(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Scale] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/scales", Some(definition)) match {
      case Some(s) ⇒ Some(ScaleReader.read(s))
      case _       ⇒ terminateWithError("Scale not created")
    }
  }

  def createSla(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Sla] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/slas", Some(definition)) match {
      case Some(s) ⇒ Some(SlaReader.read(s))
      case _       ⇒ terminateWithError("Sla not created")
    }
  }

  def updateBreed(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Breed] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/breeds/$name", Some(definition)) match {
      case Some(b) ⇒ Some(BreedReader.read(b))
      case _       ⇒ terminateWithError("Breed not updated")
    }
  }

  def updateBlueprint(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Blueprint] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/blueprints/$name", Some(definition)) match {
      case Some(b) ⇒ Some(BlueprintReader.read(b))
      case _       ⇒ terminateWithError("Blueprint not updated")
    }
  }

  def updateGateway(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Gateway] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/gateways/$name", Some(definition)) match {
      case Some(gateway) ⇒ Some(GatewayReader.read(gateway))
      case _             ⇒ terminateWithError("Gateway not updated")
    }
  }

  def updateEscalation(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Escalation] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/escalations/$name", Some(definition)) match {
      case Some(escalation) ⇒ Some(EscalationReader.read(escalation))
      case _                ⇒ terminateWithError("Escalation not updated")
    }
  }

  def updateCondition(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Condition] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/conditions/$name", Some(definition)) match {
      case Some(condition) ⇒ Some(ConditionReader.read(condition))
      case _               ⇒ terminateWithError("Condition not updated")
    }
  }

  def updateRewrite(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Rewrite] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/rewrites/$name", Some(definition)) match {
      case Some(rewrite) ⇒ Some(RewriteReader.read(rewrite))
      case _             ⇒ terminateWithError("Condition not updated")
    }
  }

  def updateRoute(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Route] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/routings/$name", Some(definition)) match {
      case Some(routing) ⇒ Some(RouteReader.read(routing))
      case _             ⇒ terminateWithError("Routing not updated")
    }
  }

  def updateScale(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Scale] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/scales/$name", Some(definition)) match {
      case Some(s) ⇒ Some(ScaleReader.read(s))
      case _       ⇒ terminateWithError("Scale not updated")
    }
  }

  def updateSla(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Sla] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/slas/$name", Some(definition)) match {
      case Some(s) ⇒ Some(SlaReader.read(s))
      case _       ⇒ terminateWithError("Sla not updated")
    }
  }

  def deleteBreed(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/breeds/$name", None)

  def deleteBlueprint(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/blueprints/$name", None)

  def deleteGateway(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/gateways/$name", None)

  def deleteEscalation(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/escalations/$name", None)

  def deleteCondition(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/conditions/$name", None)

  def deleteRewrite(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/rewrites/$name", None)

  def deleteRoute(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/routings/$name", None)

  def deleteScale(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/scales/$name", None)

  def deleteSla(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/slas/$name", None)

  def undeploy(name: String, payload: Option[String])(implicit vampHost: String): Option[String] = payload match {
    case Some(artifact) ⇒ sendAndWaitYaml(s"DELETE $vampHost/api/v1/deployments/$name", Some(artifact))
    case None           ⇒ None
  }

  def prettyJson(artifact: AnyRef) = Serialization.writePretty(artifact)

  def getBreeds(implicit vampHost: String): List[Breed] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/breeds") match {
      case Some(breeds) ⇒ yamArrayListToList(breeds).map(a ⇒ BreedReader.read(a))
      case None         ⇒ List.empty
    }

  def getBlueprints(implicit vampHost: String): List[Blueprint] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/blueprints") match {
      case Some(blueprints) ⇒ yamArrayListToList(blueprints).map(a ⇒ BlueprintReader.read(a))
      case None             ⇒ List.empty
    }

  def getGateways(implicit vampHost: String): List[Gateway] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/gateways") match {
      case Some(gateways) ⇒ yamArrayListToList(gateways).map(a ⇒ GatewayReader.read(a))
      case None           ⇒ List.empty
    }

  def getDeployments(implicit vampHost: String): List[Deployment] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/deployments") match {
      case Some(d) ⇒ yamArrayListToList(d).map(a ⇒ DeploymentReader.read(a))
      case None    ⇒ List.empty
    }

  def getEscalations(implicit vampHost: String): List[Escalation] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/escalations") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ EscalationReader.read(a))
      case None      ⇒ List.empty
    }

  def getConditions(implicit vampHost: String): List[Condition] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/conditions") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ ConditionReader.read(a))
      case None      ⇒ List.empty
    }

  def getRewrites(implicit vampHost: String): List[Rewrite] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/rewrites") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ RewriteReader.read(a))
      case None      ⇒ List.empty
    }

  def getRoutings(implicit vampHost: String): List[Route] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/routings") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ RouteReader.read(a))
      case None      ⇒ List.empty
    }

  def getScales(implicit vampHost: String): List[Scale] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/scales") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ ScaleReader.read(a))
      case None      ⇒ List.empty
    }

  def getSlas(implicit vampHost: String): List[Sla] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/slas") match {
      case Some(slas) ⇒ yamArrayListToList(slas).map(a ⇒ SlaReader.read(a))
      case None       ⇒ List.empty
    }

  def deploy(definition: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWaitYaml(s"POST $vampHost/api/v1/deployments", body = Some(definition)).map(DeploymentReader.read(_))

  def updateDeployment(deploymentId: String, definition: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWaitYaml(s"PUT $vampHost/api/v1/deployments/$deploymentId", body = Some(definition)).map(DeploymentReader.read(_))

  def getBreed(name: String)(implicit vampHost: String): Option[Breed] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/breeds/$name").map(BreedReader.read(_))

  def getBlueprint(blueprintId: String)(implicit vampHost: String): Option[Blueprint] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/blueprints/$blueprintId").map(BlueprintReader.read(_))

  def getGateway(gatewayId: String)(implicit vampHost: String): Option[Gateway] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/gateways/$gatewayId").map(GatewayReader.read(_))

  def getDeployment(name: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/deployments/$name").map(DeploymentReader.read(_))

  def getEscalation(name: String)(implicit vampHost: String): Option[Escalation] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/escalations/$name").map(EscalationReader.read(_))

  def getCondition(conditionId: String)(implicit vampHost: String): Option[Condition] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/conditions/$conditionId").map(ConditionReader.read(_))

  def getRewrite(rewriteId: String)(implicit vampHost: String): Option[Rewrite] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/rewrites/$rewriteId").map(RewriteReader.read(_))

  def getRoute(name: String)(implicit vampHost: String): Option[Route] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/routing/$name").map(RouteReader.read(_))

  def getScale(name: String)(implicit vampHost: String): Option[Scale] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/scales/$name").map(ScaleReader.read(_))

  def getSla(name: String)(implicit vampHost: String): Option[Sla] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/slas/$name").map(SlaReader.read(_))

  def info(implicit vampHost: String) =
    sendAndWaitYaml(s"GET $vampHost/api/v1/info", None)

}

trait RestSupport {
  this: CommandLineBasics ⇒

  implicit def timeout: Timeout

  implicit def system: ActorSystem

  def sendAndWaitYaml(request: String, body: Option[String] = None)(implicit m: Manifest[String]): Option[String] = {
    sendAndWait(request, body, List("Accept" -> "application/x-yaml", "Content-Type" -> "application/x-yaml", RestClient.acceptEncodingIdentity))
  }

  private def sendAndWait(request: String, body: AnyRef, headers: List[(String, String)])(implicit m: Manifest[String]): Option[String] = {
    import HttpMethods._

    try {
      val upper = request.toUpperCase
      val method = List(GET, POST, PUT, DELETE).find(method ⇒ upper.startsWith(s"${method.toString} ")).getOrElse(GET)
      val url = if (upper.startsWith(s"${method.value} ")) request.substring(s"${method.toString} ".length) else request

      val futureResult: Future[String] = new RestClient().http[String](method, url, body, headers)

      // Block until response ready (nothing else to do anyway)
      Await.result(futureResult, timeout.duration)
      futureResult.value.get match {
        case Success(result) ⇒ Some(result)
        case Failure(error)  ⇒ terminateWithError(prettyError(error))
      }
    } catch {
      case e: Exception ⇒ terminateWithError(prettyError(e))
    }
  }

  private def prettyError(error: Throwable): String = error match {
    case e: RestClientException ⇒
      e.statusCode match {
        case Some(code) ⇒ s"$code - ${extractExceptionMessage(e)}"
        case None       ⇒ s"${extractExceptionMessage(e)}"
      }
    case e: Exception ⇒ e.getMessage
  }

  private def extractExceptionMessage(e: RestClientException): String = {
    """\{\"message\":\"(.*)\"\}""".r findFirstMatchIn e.message match {
      case Some(extracted) if extracted.groupCount == 1 ⇒ extracted.group(1)
      case _ ⇒ e.getMessage
    }
  }

  protected def yamArrayListToList(ls: String): List[String] = {
    new Yaml().load(ls).asInstanceOf[util.ArrayList[java.util.Map[String, Any]]].asScala.toList.map(a ⇒ new Yaml().dumpAs(a, Tag.MAP, FlowStyle.BLOCK))
  }

}
