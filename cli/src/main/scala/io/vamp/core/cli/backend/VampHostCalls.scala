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


  def createBreed(breedDefinition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Breed] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/breeds", Some(breedDefinition)) match {
      case Some(breed) => Some(BreedReader.read(breed))
      case _ => terminateWithError("Breed not created")
        None
    }
  }

  def createBlueprint(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Blueprint] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/blueprints", Some(definition)) match {
      case Some(blueprint) => Some(BlueprintReader.read(blueprint))
      case _ => terminateWithError("Blueprint not created")
        None
    }
  }

  def createEscalation(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Escalation] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/escalations", Some(definition)) match {
      case Some(escalation) => Some(EscalationReader.read(escalation))
      case _ => terminateWithError("Escalation not created")
        None
    }
  }

  def createFilter(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Filter] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/filters", Some(definition)) match {
      case Some(filter) => Some(FilterReader.read(filter))
      case _ => terminateWithError("Filter not created")
        None
    }
  }

  def createRouting(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Routing] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/routings", Some(definition)) match {
      case Some(routing) => Some(RoutingReader.read(routing))
      case _ => terminateWithError("Routing not created")
        None
    }
  }

  def createScale(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Scale] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/scales", Some(definition)) match {
      case Some(scale) => Some(ScaleReader.read(scale))
      case _ => terminateWithError("Scale not created")
        None
    }
  }

  def createSla(definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Sla] = {
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/slas", Some(definition)) match {
      case Some(sla) => Some(SlaReader.read(sla))
      case _ => terminateWithError("Sla not created")
        None
    }
  }

  def updateBlueprint(name: String, definition: String, jsonOutput: Boolean = false)(implicit vampHost: String): Option[Blueprint] = {
    sendAndWaitYaml[String](s"PUT $vampHost/api/v1/blueprints/$name", Some(definition)) match {
      case Some(blueprint) => Some(BlueprintReader.read(blueprint))
      case _ => terminateWithError("Blueprint not updated")
        None
    }
  }


  def deleteBreed(name: String)(implicit vampHost: String) = sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/breeds/$name", None)

  def deleteBlueprint(name: String)(implicit vampHost: String) = sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/blueprints/$name", None)

  def deleteEscalation(name: String)(implicit vampHost: String) = sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/escalations/$name", None)

  def deleteFilter(name: String)(implicit vampHost: String) = sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/filters/$name", None)

  def deleteRouting(name: String)(implicit vampHost: String) = sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/routings/$name", None)

  def deleteScale(name: String)(implicit vampHost: String) = sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/scales/$name", None)

  def deleteSla(name: String)(implicit vampHost: String) = sendAndWaitYaml[Any](s"DELETE $vampHost/api/v1/slas/$name", None)


  def prettyJson(artifact: AnyRef) = Serialization.writePretty(artifact)

  /** The getXXX calls need to be refactored to use the sendAndWaitYaml call
    * After that has been done, the sendAndWait method can be removed, as well as the Deserialization class
    */


  def getBreeds(implicit vampHost: String): List[Breed] =
    sendAndWait[List[DefaultBreedSerialized]](s"GET $vampHost/api/v1/breeds") match {
      case Some(breeds) => breeds.map(breedSerialized2DefaultBreed)
      case None => List.empty
    }


  //    def getBreeds(implicit vampHost: String): List[Breed] =
  //      sendAndWaitYaml[String](s"GET $vampHost/api/v1/breeds") match {
  //        case Some(breeds) =>
  //          val yaml = new Yaml
  //          val br :List[_] = yaml.loadAll(breeds).asScala.toList
  //          br.map(a=> BreedReader.read(a.asInstanceOf[String]))
  //
  //        case None => List.empty
  //      }


  def getBlueprints(implicit vampHost: String): List[DefaultBlueprint] =
    sendAndWait[List[BlueprintSerialized]](s"GET $vampHost/api/v1/blueprints") match {
      case Some(blueprints) => blueprints.map(blueprintSerialized2DefaultBlueprint)
      case None => List.empty
    }

  def getDeployments(implicit vampHost: String): List[Deployment] =
    sendAndWait[List[DeploymentSerialized]](s"GET $vampHost/api/v1/deployments") match {
      case Some(deployments) => deployments.map(deploymentSerialized2Deployment)
      case None => List.empty
    }

  def getEscalations(implicit vampHost: String): List[Escalation] =
    sendAndWait[List[Map[String, _]]](s"GET $vampHost/api/v1/escalations") match {
      case Some(ser) => ser.map(map2Escalation)
      case None => List.empty
    }

  def getFilters(implicit vampHost: String): List[DefaultFilter] =
    sendAndWait[List[Map[String, String]]](s"GET $vampHost/api/v1/filters") match {
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

  private def nonModifyingJsonFieldTransformer: PartialFunction[JField, JField] = {
    case JField(name, value) => JField(name, value)
  }

  def createBreed(breed: DefaultBreed)(implicit vampHost: String): Option[DefaultBreed] =
    sendAndWait[DefaultBreedSerialized](s"POST $vampHost/api/v1/breeds", breed).map(breedSerialized2DefaultBreed)

  def deploy(definition: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWaitYaml[String](s"POST $vampHost/api/v1/deployments", body = Some(definition)).map(DeploymentReader.read(_))

  def updateDeployment(deploymentId: String, definition: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWaitYaml[String](s"PUT $vampHost/api/v1/deployments/$deploymentId", body = Some(definition)).map(DeploymentReader.read(_))

  def getBreed(name: String)(implicit vampHost: String): Option[Breed] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/breeds/$name").map(BreedReader.read(_))

  def getBlueprint(blueprintId: String)(implicit vampHost: String): Option[Blueprint] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/blueprints/$blueprintId").map(BlueprintReader.read(_))

  def getDeployment(name: String)(implicit vampHost: String): Option[Deployment] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/deployments/$name").map(DeploymentReader.read(_))

  def getEscalation(name: String)(implicit vampHost: String): Option[Escalation] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/escalations/$name").map(EscalationReader.read(_))

  def getFilter(filterId: String)(implicit vampHost: String): Option[Filter] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/filters/$filterId").map(FilterReader.read(_))

  def getRouting(name: String)(implicit vampHost: String): Option[Routing] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/routing/$name").map(RoutingReader.read(_))

  def getScale(name: String)(implicit vampHost: String): Option[Scale] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/scales/$name").map(ScaleReader.read(_))

  def getSla(name: String)(implicit vampHost: String): Option[Sla] =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/slas/$name").map(SlaReader.read(_))

  def info(implicit vampHost: String) =
    sendAndWaitYaml[String](s"GET $vampHost/api/v1/info", None)

}
