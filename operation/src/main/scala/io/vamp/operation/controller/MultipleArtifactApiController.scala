package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.Deployment
import io.vamp.model.reader.{ YamlReader, YamlSource, YamlSourceReader }
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

import scala.concurrent.Future

trait MultipleArtifactApiController {
  this: SingleArtifactApiController with DeploymentApiController with ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  def createArtifacts(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ createDeployment(item.input, validateOnly)
        case _                                  ⇒ createArtifact(item.kind, item.input, validateOnly)
      }
  })

  def updateArtifacts(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ updateDeployment(item.name, item.input, validateOnly)
        case _                                  ⇒ updateArtifact(item.kind, item.name, item.input, validateOnly)
      }
  })

  def deleteArtifacts(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ deleteDeployment(item.name, item.input, validateOnly)
        case _                                  ⇒ deleteArtifact(item.kind, item.name, item.input, validateOnly)
      }
  })

  private def process(source: String, execute: ArtifactSource ⇒ Future[Any]) = Future.sequence {
    ArtifactListReader.read(source).map(execute)
  }
}

case class ArtifactSource(kind: String, name: String, input: String)

object ArtifactListReader extends YamlReader[List[ArtifactSource]] {

  import YamlSourceReader._

  override def read(input: YamlSource): List[ArtifactSource] = {
    (unmarshal(input) match {
      case Left(item)   ⇒ List(item)
      case Right(items) ⇒ items
    }) map { item ⇒

      val kind = item.get[String]("kind")
      val name = item.get[String]("name")
      val source = item.flatten({ str ⇒ str != "kind" })

      ArtifactSource(if (kind.endsWith("s")) kind else s"${kind}s", name, write(source)(DefaultFormats))
    }
  }

  override protected def parse(implicit source: YamlSourceReader): List[ArtifactSource] = throw new NotImplementedError
}
