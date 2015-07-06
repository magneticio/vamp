package io.vamp.core.persistence.jdbc

import io.vamp.core.model.artifact.Port
import io.vamp.core.persistence.slick.model.PortModel
import io.vamp.core.persistence.slick.model.PortParentType._

import scala.slick.jdbc.JdbcBackend

trait PortStore {
  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._


  def createPorts(ports: List[Port], parentId: Option[Int], parentType: Option[PortParentType]): Unit = {
    for (port <- ports) Ports.add(port2PortModel(port).copy(parentId = parentId, parentType = parentType))
  }

  def deleteModelPorts(ports: List[PortModel]): Unit = for (p <- ports) Ports.deleteById(p.id.get)

  def readPortsToArtifactList(ports: List[PortModel]): List[Port] = ports.map(p => portModel2Port(p))

}
