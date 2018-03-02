package io.vamp.container_driver.marathon

import com.typesafe.scalalogging.LazyLogging
import io.vamp.common._
import io.vamp.common.util.HashUtil
import io.vamp.container_driver.ContainerDriver
import io.vamp.model.artifact._
import io.vamp.model.resolver.NamespaceValueResolver

import scala.util.Try

trait MarathonNamespace extends LazyLogging {
  this: ContainerDriver with NamespaceValueResolver with NamespaceProvider ⇒

  import MarathonDriverActor._

  private val nameDelimiter = "/"

  private lazy val tenantIdOverride: Option[String] = Try(Some(resolveWithNamespace(Config.string(s"$marathonConfig.tenant-id-override")()))).getOrElse(None)
  private lazy val tenantIdWorkflowOverride: Option[String] = Try(Some(resolveWithNamespace(Config.string(s"$marathonConfig.tenant-id-workflow-override")()))).getOrElse(None)
  private lazy val useBreedNameForServiceName: Option[Boolean] = Try(Some(Config.boolean(s"$marathonConfig.use-breed-name-for-service-name")())).getOrElse(None)

  private val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def appId(workflow: Workflow): String = {
    s"$nameDelimiter${tenantIdWorkflowOverride.getOrElse(namespace.name)}${nameDelimiter}workflow-${artifactName2Id(workflow)}"
  }

  protected def appId(deployment: Deployment, breed: Breed): String = {
    if (useBreedNameForServiceName.getOrElse(false))
      s"$nameDelimiter${tenantIdOverride.getOrElse(namespace.name)}$nameDelimiter${artifactName2Id(breed)}"
    else
      s"$nameDelimiter${tenantIdOverride.getOrElse(namespace.name)}${nameDelimiter}deployment-${artifactName2Id(deployment)}-service-${artifactName2Id(breed)}"
  }

  protected def artifactName2Id(artifact: Artifact): String = artifact.name match {
    case idMatcher(_*) ⇒ artifact.name
    case _ ⇒ artifact match {
      case lookup: Lookup ⇒ lookup.lookupName
      case _              ⇒ HashUtil.hexSha1(artifact.name)
    }
  }

  protected val namespaceConstraint: List[String] = MarathonDriverActor.namespaceConstraint()
}
