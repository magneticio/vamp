package io.vamp.model.resolver

import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.{Config, NamespaceProvider}
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.{ClusterReference, HostReference, LocalReference, _}
import io.vamp.model.notification.UnresolvedDependencyError

import scala.language.postfixOps

private case class HostPortClusterReference(host: HostReference, port: TraitReference) extends ClusterReference {

  override def cluster: String = host.cluster

  override def reference: String = port.reference
}

trait DeploymentValueResolver extends ValueResolver with ConfigurationValueResolver with ClassLoaderValueResolver with LazyLogging {
  this: NamespaceProvider with NotificationProvider ⇒

  private val resolversPath = "vamp.model.resolvers.deployment"

  override def resolverClasses: List[String] = if (Config.has(resolversPath)(namespace)()) Config.stringList(resolversPath)() else Nil

  override def resolve(value: String, provider: (ValueReference ⇒ String)): String = {

    def hostPort(nodes: List[TraitResolverNode]): List[TraitResolverNode] = nodes match {

      case VariableNode(h: HostReference) :: StringNode(":") :: VariableNode(t: TraitReference) :: tail ⇒

        if (h.cluster == t.cluster && TraitReference.groupFor(t.group).contains(TraitReference.Ports))
          VariableNode(HostPortClusterReference(h, t)) :: hostPort(tail)
        else
          VariableNode(h: HostReference) :: StringNode(":") :: VariableNode(t: TraitReference) :: hostPort(tail)

      case head :: tail ⇒ head :: hostPort(tail)
      case Nil          ⇒ Nil
    }

    hostPort(nodes(value)).map {
      case StringNode(string)      ⇒ string
      case VariableNode(reference) ⇒ provider(reference)
    } mkString
  }

  def resolveEnvironmentVariables(deployment: Deployment, clusters: List[DeploymentCluster]): List[EnvironmentVariable] = {
    deployment.environmentVariables.map(ev ⇒ TraitReference.referenceFor(ev.name) match {
      case Some(TraitReference(c, g, _)) if g == TraitReference.groupFor(TraitReference.EnvironmentVariables) && ev.interpolated.isEmpty && ev.value.isDefined && clusters.exists(_.name == c) ⇒
        ev.copy(alias = None, interpolated = Some(resolve(ev.value.get, valueFor(deployment, None))))
      case _ ⇒ ev
    })
  }

  def matchDependency(dependency: Breed)(breed: Breed): Boolean = {
    if (breed.name != dependency.name) {
      if (dependency.name.endsWith("*")) {
        val startsWith = dependency.name.substring(0, dependency.name.length - 1)
        breed.name.startsWith(startsWith)
      }
      else false
    }
    else true
  }

  def valueForWithDependencyReplacement(deployment: Deployment, service: DeploymentService)(reference: ValueReference): String = {
    val aliases = service.breed.dependencies.map {
      case (alias, dependency) ⇒ alias → (deployment.clusters.find(_.services.exists(service ⇒ matchDependency(dependency)(service.breed))) match {
        case Some(cluster) ⇒ cluster.name
        case None          ⇒ throwException(UnresolvedDependencyError(service.breed, dependency))
      })
    }

    val updated = reference match {
      case ref @ TraitReference(cluster, _, _) ⇒ ref.copy(cluster = aliases.getOrElse(cluster, cluster))
      case HostReference(cluster)              ⇒ HostReference(aliases.getOrElse(cluster, cluster))
      case HostPortClusterReference(h, t)      ⇒ HostPortClusterReference(HostReference(aliases.getOrElse(h.cluster, h.cluster)), t.copy(cluster = aliases.getOrElse(t.cluster, t.cluster)))
      case ref                                 ⇒ ref
    }

    valueFor(deployment, Option(service))(updated)
  }

  def valueFor(deployment: Deployment, service: Option[DeploymentService])(reference: ValueReference): String = (
    valueForDeploymentService(deployment, service)
    orElse {
      logger.info("DeploymentValueResolver - returning super[ClassLoaderValueResolver] for deployment {}", deployment.name)
      super[ClassLoaderValueResolver].valueForReference((deployment, service))
    }
    orElse {
      logger.info("DeploymentValueResolver - returning super[ConfigurationValueResolver] for deployment {}", deployment.name)
      super[ConfigurationValueResolver].valueForReference
    }
    orElse {
      logger.info("DeploymentValueResolver - returning empty partial function for deployment {}", deployment.name)
      PartialFunction[ValueReference, String] { _ ⇒ "" }
    }
  )(reference)

  override def valueForReference: PartialFunction[ValueReference, String] = PartialFunction.empty

  private def valueForDeploymentService(deployment: Deployment, service: Option[DeploymentService]): PartialFunction[ValueReference, String] = {

    case ref: TraitReference ⇒ {
      val trainRef = deployment.traits.find(_.name == ref.reference).flatMap(_.value).getOrElse("")
      logger.info("valueForDeploymentService TraitReference {} value {}", ref.reference, trainRef)
      trainRef
    }

    case ref: HostReference  ⇒ {
      val hostRef = deployment.hosts.find(_.name == ref.asTraitReference).flatMap(_.value).getOrElse("")
      logger.info("valueForDeploymentService HostReference {} value {}", ref.asTraitReference, hostRef)
      hostRef
    }

    case ref: LocalReference ⇒ {
      val lockRef = service.flatMap(service ⇒ (service.environmentVariables ++ service.breed.constants).find(_.name == ref.name).flatMap(_.value)).getOrElse("")
      logger.info("valueForDeploymentService LocalReference {} value {}", ref.name, lockRef)
      lockRef
    }

    case HostPortClusterReference(_, t) ⇒ {
      val hostPortClusterReference = {
        for {
          port ← deployment.ports.find(_.name == t.reference).map(port ⇒ port.number)
          host ← deployment.clusters.find(_.name == t.cluster).flatMap(_.serviceBy(t.name).map(_.host))
        } yield s"$host:$port"
      } getOrElse ""
      logger.info("valueForDeploymentService HostPortClusterReference {} value {}", t.reference, hostPortClusterReference)
      hostPortClusterReference
    }
  }
}
