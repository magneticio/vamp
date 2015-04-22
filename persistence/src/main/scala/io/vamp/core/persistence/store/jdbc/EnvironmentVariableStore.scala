package io.vamp.core.persistence.store.jdbc

import com.typesafe.scalalogging.Logger
import io.vamp.core.model.artifact.EnvironmentVariable
import io.vamp.core.persistence.notification.PersistenceNotificationProvider
import io.vamp.core.persistence.slick.model.EnvironmentVariableModel
import io.vamp.core.persistence.slick.model.EnvironmentVariableParentType.EnvironmentVariableParentType
import org.slf4j.LoggerFactory

import scala.slick.jdbc.JdbcBackend


trait EnvironmentVariableStore extends PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._

  private val logger = Logger(LoggerFactory.getLogger(classOf[EnvironmentVariableStore]))

  protected def createEnvironmentVariables(envVars: List[EnvironmentVariable], t: EnvironmentVariableParentType, parentId: Int, deploymentId: Option[Int]): Unit =
    for (env <- envVars)
      EnvironmentVariables.add(EnvironmentVariableModel(deploymentId = deploymentId, name = env.name, alias = env.alias, value = env.value, parentId = Some(parentId), parentType = Some(t)))

  protected def deleteEnvironmentVariables(envVars: List[EnvironmentVariableModel]): Unit =
    for (e <- envVars)
      EnvironmentVariables.deleteById(e.id.get)

}
