package io.vamp.persistence.jdbc

import io.vamp.persistence.notification.{ PersistenceNotificationProvider, UnsupportedParameterToPersist }
import io.vamp.persistence.slick.model.ParameterParentType._
import io.vamp.persistence.slick.model.{ ParameterModel, ParameterType }

import scala.slick.jdbc.JdbcBackend

trait ParameterStore extends PersistenceNotificationProvider {
  implicit val sess: JdbcBackend.Session

  import io.vamp.persistence.slick.components.Components.instance._

  protected def createParameters(parameters: Map[String, Any], parentId: Int, parentType: ParameterParentType): Unit = {
    parameters.map(param ⇒
      param._2 match {
        case i: Int    ⇒ Parameters.add(ParameterModel(deploymentId = None, name = param._1, intValue = i, parameterType = ParameterType.Int, parentType = parentType, parentId = parentId))
        case d: Double ⇒ Parameters.add(ParameterModel(deploymentId = None, name = param._1, doubleValue = d, parameterType = ParameterType.Double, parentType = parentType, parentId = parentId))
        case s: String ⇒ Parameters.add(ParameterModel(deploymentId = None, name = param._1, stringValue = Some(s), parameterType = ParameterType.String, parentType = parentType, parentId = parentId))
        case e         ⇒ throwException(UnsupportedParameterToPersist(name = param._1, parent = parentType.toString, parameterType = e.getClass.toString))
      }
    )
  }

  protected def deleteExistingParameters(parameters: List[ParameterModel]): Unit =
    for (param ← parameters) Parameters.deleteById(param.id.get)

  protected def parametersToArtifact(e: List[ParameterModel]): Map[String, Any] = {
    (for {
      param ← e
      value = param.parameterType match {
        case ParameterType.Int    ⇒ param.intValue
        case ParameterType.Double ⇒ param.doubleValue
        case ParameterType.String ⇒ param.stringValue.get
      }
    } yield param.name -> value).toMap
  }

}
