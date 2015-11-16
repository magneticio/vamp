package io.vamp.cli.backend

import java.util

import io.vamp.common.http.RestClient.Method
import io.vamp.common.http.{ RestApiContentTypes, RestApiMarshaller, RestClient, RestClientException }
import io.vamp.common.http.{ RestApiContentTypes, RestApiMarshaller, RestClient }
import io.vamp.cli.commandline.CommandLineBasics
import io.vamp.cli.commands.IoUtils
import io.vamp.model.artifact._
import io.vamp.model.reader._
import org.json4s.native._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.{ implicitConversions, postfixOps }
import scala.util.{ Failure, Success }

object VampHostCalls extends RestSupport with RestApiMarshaller with RestApiContentTypes with CommandLineBasics with IoUtils {

  val timeout = 30 seconds

  def getDeploymentAsBlueprint(deploymentId: String)(implicit vampHost: String): Option[Blueprint] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/deployments/$deploymentId?as_blueprint=true").map(BlueprintReader.read(_))

  def createBreed(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Breed] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/breeds", Some(definition)) match {
      case Some(breed) ⇒ Some(BreedReader.read(breed))
      case _           ⇒ terminateWithError("Breed not created")
    }
  }

  def createBlueprint(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Blueprint] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/blueprints", Some(definition)) match {
      case Some(blueprint) ⇒ Some(BlueprintReader.read(blueprint))
      case _               ⇒ terminateWithError("Blueprint not created")
    }
  }

  def createEscalation(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Escalation] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/escalations", Some(definition)) match {
      case Some(escalation) ⇒ Some(EscalationReader.read(escalation))
      case _                ⇒ terminateWithError("Escalation not created")
    }
  }

  def createFilter(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Filter] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/filters", Some(definition)) match {
      case Some(filter) ⇒ Some(FilterReader.read(filter))
      case _            ⇒ terminateWithError("Filter not created")
    }
  }

  def createRouting(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Routing] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/routings", Some(definition)) match {
      case Some(routing) ⇒ Some(RoutingReader.read(routing))
      case _             ⇒ terminateWithError("Routing not created")
    }
  }

  def createScale(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Scale] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/scales", Some(definition)) match {
      case Some(scale) ⇒ Some(ScaleReader.read(scale))
      case _           ⇒ terminateWithError("Scale not created")
    }
  }

  def createSla(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Sla] = {
    sendAndWaitYaml(s"POST $vampHost/api/v1/slas", Some(definition)) match {
      case Some(sla) ⇒ Some(SlaReader.read(sla))
      case _         ⇒ terminateWithError("Sla not created")
    }
  }

  def updateBreed(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Breed] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/breeds/$name", Some(definition)) match {
      case Some(breed) ⇒ Some(BreedReader.read(breed))
      case _           ⇒ terminateWithError("Breed not updated")
    }
  }

  def updateBlueprint(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Blueprint] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/blueprints/$name", Some(definition)) match {
      case Some(blueprint) ⇒ Some(BlueprintReader.read(blueprint))
      case _               ⇒ terminateWithError("Blueprint not updated")
    }
  }

  def updateEscalation(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Escalation] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/escalations/$name", Some(definition)) match {
      case Some(escalation) ⇒ Some(EscalationReader.read(escalation))
      case _                ⇒ terminateWithError("Escalation not updated")
    }
  }

  def updateFilter(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Filter] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/filters/$name", Some(definition)) match {
      case Some(filter) ⇒ Some(FilterReader.read(filter))
      case _            ⇒ terminateWithError("Filter not updated")
    }
  }

  def updateRouting(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Routing] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/routings/$name", Some(definition)) match {
      case Some(routing) ⇒ Some(RoutingReader.read(routing))
      case _             ⇒ terminateWithError("Routing not updated")
    }
  }

  def updateScale(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Scale] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/scales/$name", Some(definition)) match {
      case Some(scale) ⇒ Some(ScaleReader.read(scale))
      case _           ⇒ terminateWithError("Scale not updated")
    }
  }

  def updateSla(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Sla] = {
    sendAndWaitYaml(s"PUT $vampHost/api/v1/slas/$name", Some(definition)) match {
      case Some(sla) ⇒ Some(SlaReader.read(sla))
      case _         ⇒ terminateWithError("Sla not updated")
    }
  }

  def deleteBreed(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/breeds/$name", None)

  def deleteBlueprint(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/blueprints/$name", None)

  def deleteEscalation(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/escalations/$name", None)

  def deleteFilter(name: String)(implicit vampHost: String) =
    sendAndWaitYaml(s"DELETE $vampHost/api/v1/filters/$name", None)

  def deleteRouting(name: String)(implicit vampHost: String) =
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

  def getDeployments(implicit vampHost: String): List[Deployment] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/deployments") match {
      case Some(deployments) ⇒ yamArrayListToList(deployments).map(a ⇒ DeploymentReader.read(a))
      case None              ⇒ List.empty
    }

  def getEscalations(implicit vampHost: String): List[Escalation] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/escalations") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ EscalationReader.read(a))
      case None      ⇒ List.empty
    }

  def getFilters(implicit vampHost: String): List[Filter] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/filters") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ FilterReader.read(a))
      case None      ⇒ List.empty
    }

  def getRoutings(implicit vampHost: String): List[Routing] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/routings") match {
      case Some(ser) ⇒ yamArrayListToList(ser).map(a ⇒ RoutingReader.read(a))
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

  def getDeployment(name: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/deployments/$name").map(DeploymentReader.read(_))

  def getEscalation(name: String)(implicit vampHost: String): Option[Escalation] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/escalations/$name").map(EscalationReader.read(_))

  def getFilter(filterId: String)(implicit vampHost: String): Option[Filter] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/filters/$filterId").map(FilterReader.read(_))

  def getRouting(name: String)(implicit vampHost: String): Option[Routing] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/routing/$name").map(RoutingReader.read(_))

  def getScale(name: String)(implicit vampHost: String): Option[Scale] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/scales/$name").map(ScaleReader.read(_))

  def getSla(name: String)(implicit vampHost: String): Option[Sla] =
    sendAndWaitYaml(s"GET $vampHost/api/v1/slas/$name").map(SlaReader.read(_))

  def info(implicit vampHost: String) =
    sendAndWaitYaml(s"GET $vampHost/api/v1/info", None)

}

trait RestSupport {
  this: CommandLineBasics ⇒

  def timeout: Duration

  def sendAndWaitYaml(request: String, body: Option[String] = None)(implicit m: Manifest[String]): Option[String] =
    sendAndWait(request, body, List("Accept" -> "application/x-yaml", "Content-Type" -> "application/x-yaml", RestClient.acceptEncodingIdentity))

  private def sendAndWait(request: String, body: AnyRef, headers: List[(String, String)])(implicit m: Manifest[String]): Option[String] = {
    try {
      val upper = request.toUpperCase
      val method = Method.values.find(method ⇒ upper.startsWith(s"${method.toString} ")).getOrElse(Method.GET)
      val url = if (upper.startsWith(s"${method.toString} ")) request.substring(s"${method.toString} ".length) else request

      val futureResult: Future[String] = RestClient.http[String](method, url, body, headers)

      // Block until response ready (nothing else to do anyway)
      Await.result(futureResult, timeout)
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
