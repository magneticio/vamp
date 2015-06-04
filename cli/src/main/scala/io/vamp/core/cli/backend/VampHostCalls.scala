package io.vamp.core.cli.backend

import io.vamp.common.http.{RestApiContentTypes, RestApiMarshaller, RestClient}
import io.vamp.core.cli.commandline.CommandLineBasics
import io.vamp.core.cli.serializers.Deserialization
import io.vamp.core.model.artifact._
import io.vamp.core.model.reader._
import io.vamp.core.model.serialization.CoreSerializationFormat
import org.json4s.JsonAST._
import org.json4s.native._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Success}

object VampHostCalls extends Deserialization with RestApiMarshaller with RestApiContentTypes with CommandLineBasics {

  implicit val formats = CoreSerializationFormat.default
  val timeout = 30 seconds

  def getDeploymentAsBlueprint(deploymentId: String)(implicit vampHost: String): Option[Blueprint] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/deployments/$deploymentId?as_blueprint=true").map(BlueprintReader.read(_))

  private def sendAndWaitYaml[A](request: String, body: Option[String] = None): Option[A] = {
    try {
      val futureResult: Future[A] = YamlRestClient.request(request, body = body)
      // Block until response ready (nothing else to do anyway)
      Await.result(futureResult, timeout)
      futureResult.value.get match {
        case Success(result) => Some(result)
        case Failure(error) => terminateWithError(error.getMessage)
          None
      }
    }
    catch {
      case e: Exception => terminateWithError(e.getMessage)
        None
    }
  }

  def updateDeployment(deploymentId: String, blueprint: DefaultBlueprint)(implicit vampHost: String): Option[Deployment] =
    sendAndWait[DeploymentSerialized](s"PUT $vampHost/api/v1/deployments/$deploymentId", body = blueprint).map(deploymentSerialized2Deployment)

  def getBreed(name: String)(implicit vampHost: String): Option[Breed] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/breeds/$name").map(BreedReader.read(_))

  def createBreed(breedDefinition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Breed] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/breeds", Some(breedDefinition)) match {
      case Some(breed) => Some(BreedReader.read(breed))
      case _ => terminateWithError("Breed not created")
        None
    }
  }

  def prettyJson(artifact: AnyRef) = Serialization.writePretty(artifact)

  def getBreeds(implicit vampHost: String): List[Breed] =
    sendAndWait[List[DefaultBreedSerialized]](s"GET $vampHost/api/v1/breeds") match {
      case Some(breeds) => breeds.map(breedSerialized2DefaultBreed)
      case None => List.empty
    }

  def getEscalations(implicit vampHost: String): List[Escalation] =
    sendAndWait[List[Map[String, _]]](s"GET $vampHost/api/v1/escalations") match {
      case Some(ser) => ser.map(map2Escalation)
      case None => List.empty
    }

  def getFilters(implicit vampHost: String): List[DefaultFilter] =
    sendAndWait[List[Map[String,String]]](s"GET $vampHost/api/v1/filters") match {
      case Some(ser) => ser.map(filterSerialized2DefaultFilter)
      case None => List.empty
    }

  def getRoutings(implicit vampHost: String): List[Routing] =
    sendAndWait[List[RoutingSerialized]](s"GET $vampHost/api/v1/routings") match {
      case Some(ser) => ser.map(routingSerialized2DefaultRouting)
      case None => List.empty
    }

  def getScales(implicit vampHost: String): List[Scale] =
    sendAndWait[List[ScaleSerialized]](s"GET $vampHost/api/v1/scales") match {
      case Some(ser) => ser.map(scaleSerializedToScale)
      case None => List.empty
    }


  /**
   * Send a message to the vamp host
   * @deprecated  Use sendAndWaitYaml instead
   * @param request
   * @param body
   * @param m
   * @tparam A
   * @return
   */
  private def sendAndWait[A](request: String, body: AnyRef = None)(implicit m: Manifest[A]): Option[A] = {
    try {
      val futureResult: Future[A] = RestClient.request[A](request, body = body, jsonFieldTransformer = nonModifyingJsonFieldTransformer)
      // Block until response ready (nothing else to do anyway)
      Await.result(futureResult, timeout)
      futureResult.value.get match {
        case Success(result) => Some(result)
        case Failure(error) => terminateWithError(error.getMessage)
          None
      }
    }
    catch {
      case e: Exception => terminateWithError(e.getMessage)
        None
    }
  }

  private def nonModifyingJsonFieldTransformer: PartialFunction[JField, JField] = {
    case JField(name, value) => JField(name, value)
  }

  def createBreed(breed: DefaultBreed)(implicit vampHost: String): Option[DefaultBreed] =
    sendAndWait[DefaultBreedSerialized](s"POST $vampHost/api/v1/breeds", breed).map(breedSerialized2DefaultBreed)

  def deleteBreed(breedId: String)(implicit vampHost: String) =
    sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/breeds/$breedId", None)

  def getBlueprint(blueprintId: String)(implicit vampHost: String): Option[Blueprint] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/blueprints/$blueprintId").map(BlueprintReader.read(_))

  def getBlueprints(implicit vampHost: String): List[DefaultBlueprint] =
    sendAndWait[List[BlueprintSerialized]](s"GET $vampHost/api/v1/blueprints") match {
      case Some(blueprints) => blueprints.map(blueprintSerialized2DefaultBlueprint)
      case None => List.empty
    }

  def getDeployment(deploymentName: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWait[DeploymentSerialized](s"GET $vampHost/api/v1/deployments/$deploymentName").map(deploymentSerialized2Deployment)

  def getDeployments(implicit vampHost: String): List[Deployment] =
    sendAndWait[List[DeploymentSerialized]](s"GET $vampHost/api/v1/deployments") match {
      case Some(deployments) => deployments.map(deploymentSerialized2Deployment)
      case None => List.empty
    }

  def getFilter(filterId: String)(implicit vampHost: String): Option[Filter] = {
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/filters/$filterId") match {
      case Some(filter) => Some(FilterReader.read(filter))
      case _ => terminateWithError("Filter not found")
        None
    }
  }

  def getEscalation(name: String)(implicit vampHost: String): Option[Escalation] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/escalations/$name").map(EscalationReader.read(_))

  def info(implicit vampHost: String) =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/info", None)

  def getRouting(name: String)(implicit vampHost: String): Option[Routing] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/routing/$name").map(RoutingReader.read(_))

  def getScale(name: String)(implicit vampHost: String): Option[Scale] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/scales/$name").map(ScaleReader.read(_))

  def getSla(name: String)(implicit vampHost: String): Option[Sla] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/slas/$name").map(SlaReader.read(_))

  def getSlas(implicit vampHost: String): List[Sla] =
    sendAndWait[List[Map[String, _]]](s"GET $vampHost/api/v1/slas") match {
      case Some(slas) => slas.flatMap(sla => mapToSla(Some(sla)))
      case None => List.empty
    }


}
