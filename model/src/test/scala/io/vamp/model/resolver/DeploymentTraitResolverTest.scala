package io.vamp.model.resolver

import io.vamp.model.artifact._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class DeploymentTraitResolverTest extends FlatSpec with Matchers with DeploymentTraitResolver {

  "DeploymentTraitResolver" should "pass through environment variables for an empty cluster list" in {

    val environmentVariables = EnvironmentVariable("backend1.environment_variables.port", None, Some("5555")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some(s"$$backend1.host")) :: Nil

    val filter = environmentVariables.map(_.name).toSet

    resolveEnvironmentVariables(deployment(environmentVariables), Nil).filter(ev ⇒ filter.contains(ev.name)) should equal(
      environmentVariables
    )
  }

  it should "pass through environment variables for non relevant clusters" in {

    val environmentVariables = EnvironmentVariable("backend1.environment_variables.port", None, Some("5555")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some(s"$$backend1.host")) :: Nil

    val filter = environmentVariables.map(_.name).toSet

    resolveEnvironmentVariables(deployment(environmentVariables), DeploymentCluster("backend", Nil, None, None) :: Nil).filter(ev ⇒ filter.contains(ev.name)) should equal(
      environmentVariables
    )
  }

  it should "interpolate simple reference" in {

    val environmentVariables = EnvironmentVariable("backend.environment_variables.port", None, Some(s"$$frontend.constants.const1")) ::
      EnvironmentVariable("backend.environment_variables.timeout", None, Some(s"$${backend1.constants.const2}")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some(s"$${frontend.constants.const1}")) :: Nil

    val filter = environmentVariables.map(_.name).toSet

    resolveEnvironmentVariables(deployment(environmentVariables), DeploymentCluster("backend", Nil, None, None) :: Nil).filter(ev ⇒ filter.contains(ev.name)) should equal(
      EnvironmentVariable("backend.environment_variables.port", None, Some(s"$$frontend.constants.const1"), interpolated = Some("9050")) ::
        EnvironmentVariable("backend.environment_variables.timeout", None, Some(s"$${backend1.constants.const2}"), interpolated = Some(s"$$backend1.host")) ::
        EnvironmentVariable("backend1.environment_variables.timeout", None, Some(s"$${frontend.constants.const1}"), interpolated = None) :: Nil
    )
  }

  it should "interpolate complex value" in {

    val environmentVariables = EnvironmentVariable("backend.environment_variables.url", None, Some("http://$backend1.host:$frontend.constants.const1/api/$$/$backend1.environment_variables.timeout")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some("4000")) :: Nil

    val filter = environmentVariables.map(_.name).toSet

    resolveEnvironmentVariables(deployment(environmentVariables), DeploymentCluster("backend", Nil, None, None) :: Nil).filter(ev ⇒ filter.contains(ev.name)) should equal(
      EnvironmentVariable("backend.environment_variables.url", None, Some("http://$backend1.host:$frontend.constants.const1/api/$$/$backend1.environment_variables.timeout"), interpolated = Some("http://vamp.io:9050/api/$/4000")) ::
        EnvironmentVariable("backend1.environment_variables.timeout", None, Some("4000"), interpolated = None) :: Nil
    )
  }

  def deployment(environmentVariables: List[EnvironmentVariable]) = {
    val clusters = DeploymentCluster("backend1", Nil, None, None) :: DeploymentCluster("backend2", Nil, None, None) :: Nil
    val addition = EnvironmentVariable("frontend.constants.const1", None, Some("9050")) :: EnvironmentVariable("backend1.constants.const2", None, Some(s"$$backend1.host")) :: Nil
    val hosts = Host("backend1.hosts.host", Some("vamp.io")) :: Nil
    Deployment("", clusters, Nil, Nil, environmentVariables ++ addition, hosts)
  }
}
