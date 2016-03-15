package io.vamp.container_driver.docker

import com.spotify.docker.client.{ DockerClient, DefaultDockerClient }
import com.spotify.docker.client.messages.{ ImageInfo, HostConfig, PortBinding, ContainerConfig, ContainerInfo ⇒ spContainerInfo, Container ⇒ spContainer, ContainerCreation, Info }
import java.lang.reflect.Field

import io.vamp.container_driver.{ AbstractContainerDriver, ContainerPortMapping, ContainerInfo, ContainerService, ContainerInstance }
import io.vamp.model.artifact._
import io.vamp.model.reader.MegaByte

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.mutable.{ Map ⇒ MutableMap }

import com.typesafe.config.ConfigFactory

import spray.json._

import org.joda.time.DateTime

class RawDockerClient(client: DefaultDockerClient) {
  import RawDockerClient._

  def asyncCall[A, B](f: Option[A] ⇒ Option[B])(implicit ec: ExecutionContext): Option[A] ⇒ Future[Option[B]] = a ⇒ Future { f(a) }

  def internalCreateContainer = lift[(ContainerConfig, String), ContainerCreation] { x ⇒ client.createContainer(x._1, x._2) }

  def internalStartContainer = lift[String, Unit](client.startContainer(_))

  def internalGetContainerInfo = lift[String, spContainerInfo](client.inspectContainer(_))

  def internalUndeployContainer = lift[String, Unit](client.killContainer(_))

  def internalAllContainer = lift[DockerClient.ListContainersParam, java.util.List[spContainer]]({ client.listContainers(_) })

  def internalInfo = lift[Unit, Info](Unit ⇒ client.info())

  def pullImage = lift[String, Unit]({ image ⇒
    if (scala.util.Try(client.inspectImage(image)).isFailure)
      client.pull(image)
  })

  def checkLocalImage = lift[String, ImageInfo](client.inspectImage(_))

}

object RawDockerClient {
  import scala.collection.JavaConversions._

  def lift[A, B](f: A ⇒ B): Option[A] ⇒ Option[B] = _ map f

  def Try[A](cl: Class[A], paramKey: String): Either[String, Field] = {
    try {
      Right(cl.getClass.getField(paramKey))
    } catch {
      case e: NoSuchFieldException ⇒ Left(e.getMessage)
      case s: SecurityException    ⇒ Left(s.getMessage)
      case u: Throwable            ⇒ Left(u.getMessage)
    }
  }

  def translateToRaw(container: Option[Container], service: DeploymentService): Option[ContainerConfig] = {
    import DefaultScaleProtocol.DefaultScaleFormat

    val spContainer = ContainerConfig.builder()
    val hostConfig = HostConfig.builder()

    container match {
      case Some(container) ⇒ {
        spContainer.image(container.docker.image)
        val mutableHash: java.util.Map[String, java.util.List[PortBinding]] = new java.util.HashMap[String, java.util.List[PortBinding]]()
        val hostPorts: java.util.List[PortBinding] = new java.util.ArrayList[PortBinding]()

        container.docker.portMappings.map(x ⇒ {
          hostPorts.add(PortBinding.of("0.0.0.0", x.containerPort))
          mutableHash.put(x.containerPort.toString(), hostPorts)
        })

        val labels: MutableMap[String, String] = MutableMap()
        if (service.scale != None)
          labels += ("scale" -> service.scale.get.toJson.toString())

        if (service.dialects.contains(Dialect.Docker)) {
          service.dialects.get(Dialect.Docker).map { x ⇒
            val values = x.asInstanceOf[Map[String, Any]]

            /* Looking for labels */
            val inLabels = values.get("labels").asInstanceOf[Option[Map[String, String]]]
            if (inLabels != None)
              inLabels.get.foreach(f ⇒ { labels += f })
            spContainer.labels(labels)

            /* Getting net parameters */
            val net = values.get("net").asInstanceOf[Option[String]]
            if (net != None) {
              spContainer.hostConfig(hostConfig.portBindings(mutableHash).networkMode(net.get).build())
            } else
              spContainer.hostConfig(hostConfig.portBindings(mutableHash).networkMode("bridge").build())
          }
        }

        val spCont = spContainer.build()
        Some(spCont)
      }
      case _ ⇒ None
    }
  }

  def translateFromRaw(creation: ContainerCreation): String =
    creation.id

  def translateFromspContainerToApp(container: spContainer): App = {
    import DefaultScaleProtocol.DefaultScaleFormat
    import spray.json._

    val containerName = { if (container.names().size() > 0) container.names().head.substring(1) else "?" }
    val defaultScaleJs = container.labels().getOrDefault("scale", "")

    if (!defaultScaleJs.isEmpty()) {
      val defaultScale = defaultScaleJs.parseJson.convertTo[DefaultScale]
      App(containerName, defaultScale.instances, defaultScale.cpu, defaultScale.memory.value, List(Task(container.id(), containerName, "", if (!container.ports().isEmpty()) container.ports().map { x ⇒ x.getPublicPort }.toList else List(0), Some(new DateTime(container.created()).toString()))))
    } else {
      /* Default app values. They are going to be stored as label */
      App(containerName, 1, 0.0, 0.0, List(Task(container.id(), containerName, "", if (!container.ports().isEmpty()) container.ports().map { x ⇒ x.getPublicPort }.toList else List(0), Some(new DateTime(container.created()).toString()))))
    }
  }

  def translateInfoToContainerInfo(info: Info): ContainerInfo = {
    ContainerInfo(info.id(), Unit)
  }
}