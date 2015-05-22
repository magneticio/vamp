package io.vamp.core.cli.backend

import io.vamp.common.http.RestClient
import io.vamp.core.cli.commandline.CommandLineBasics
import io.vamp.core.cli.serializers.Deserialization
import io.vamp.core.model.artifact._
import io.vamp.core.model.reader.BreedReader
import io.vamp.core.model.serialization.SerializationFormat
import io.vamp.core.rest_api.{RestApiContentTypes, RestApiMarshaller}
import org.json4s.JsonAST._
import org.json4s.native._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Success}


object VampHostCalls extends Deserialization with RestApiMarshaller with RestApiContentTypes with CommandLineBasics {

  implicit val formats = SerializationFormat.default
  val timeout = 30 seconds

  def getDeploymentAsBlueprint(deploymentId: String)(implicit vampHost: String): Option[DefaultBlueprint] =
    sendAndWait[BlueprintSerialized](s"GET $vampHost/api/v1/deployments/$deploymentId?as_blueprint=true").map(blueprintSerialized2DefaultBlueprint)

  def updateDeployment(deploymentId: String, blueprint: DefaultBlueprint)(implicit vampHost: String): Option[Deployment] =
    sendAndWait[DeploymentSerialized](s"PUT $vampHost/api/v1/deployments/$deploymentId", body = blueprint).map(deploymentSerialized2Deployment)

  def getBreed(breedId: String)(implicit vampHost: String): Breed = {
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/breeds/$breedId") match {
      case Some(breed) => BreedReader.read(breed)
      case _ => terminateWithError("Breed not found")
        BreedReference(name = "Breed not found")
    }
  }


  def createBreed(breedFile: String)(implicit vampHost: String): String = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/breeds", Some(breedFile)) match {
      case Some(breed) => breed //BreedReader.read(breed)
      case _ => terminateWithError("Breed not created")
        //BreedReference(name = "Breed not created")
        ""
    }
  }

  def getBreeds(implicit vampHost: String): List[Breed] =
    sendAndWait[List[DefaultBreedSerialized]](s"GET $vampHost/api/v1/breeds") match {
      case Some(breeds) => breeds.map(breedSerialized2DefaultBreed)
      case None => List.empty
    }

  //  sendAndWaitYaml(s"GET $vampHost/api/v1/breeds") match {
  //    case Some(breeds) => breeds.map(BreedReader.read(_))
  //    case  _ => List.empty
  //  }


  def createBreed(breed: DefaultBreed)(implicit vampHost: String): Option[DefaultBreed] =
    sendAndWait[DefaultBreedSerialized](s"POST $vampHost/api/v1/breeds", breed).map(breedSerialized2DefaultBreed)

  def deleteBreed(breedId: String)(implicit vampHost: String) =
    sendAndWait[Any](s"DELETE $vampHost/api/v1/breeds/$breedId", None)

  def getBlueprint(blueprintId: String)(implicit vampHost: String): Option[DefaultBlueprint] =
    sendAndWait[BlueprintSerialized](s"GET $vampHost/api/v1/blueprints/$blueprintId").map(blueprintSerialized2DefaultBlueprint)

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

  def info(implicit vampHost: String) =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/info", None)

  def getSlas(implicit vampHost: String): List[Sla] =
    sendAndWait[List[Map[String, _]]](s"GET $vampHost/api/v1/slas") match {
      case Some(slas) => slas.flatMap(sla => mapToSla(Some(sla)))
      case None => List.empty
    }


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

  private def nonModifyingJsonFieldTransformer: PartialFunction[JField, JField] = {
    case JField(name, value) => JField(name, value)
  }

  def prettyJson(artifact: AnyRef) = Serialization.writePretty(artifact)

}
