package io.vamp.core.model.resolver

import io.vamp.core.model.artifact._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class DeploymentTraitResolverTest extends FlatSpec with Matchers with DeploymentTraitResolver {

  "DeploymentTraitResolver" should "should pass through environment variables for an empty cluster list" in {

    val environmentVariables = EnvironmentVariable("backend1.environment_variables.port", None, Some("5555")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some("$backend1.host")) :: Nil

    resolveEnvironmentVariables(deployment(environmentVariables), Nil) should equal(
      environmentVariables
    )
  }

  it should "should pass through environment variables for non relevant clusters" in {

    val environmentVariables = EnvironmentVariable("backend1.environment_variables.port", None, Some("5555")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some("$backend1.host")) :: Nil

    resolveEnvironmentVariables(deployment(environmentVariables), DeploymentCluster("backend", Nil, None) :: Nil) should equal(
      environmentVariables
    )
  }

  it should "should interpolate simple reference" in {

    val environmentVariables = EnvironmentVariable("backend.environment_variables.port", None, Some("$frontend.constants.const1")) ::
      EnvironmentVariable("backend.environment_variables.timeout", None, Some("${backend1.constants.const2}")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some("${frontend.constants.const1}")) :: Nil

    resolveEnvironmentVariables(deployment(environmentVariables), DeploymentCluster("backend", Nil, None) :: Nil) should equal(
      EnvironmentVariable("backend.environment_variables.port", None, Some("$frontend.constants.const1"), interpolated = Some("9050")) ::
        EnvironmentVariable("backend.environment_variables.timeout", None, Some("${backend1.constants.const2}"), interpolated = Some("$backend1.host")) ::
        EnvironmentVariable("backend1.environment_variables.timeout", None, Some("${frontend.constants.const1}"), interpolated = None) :: Nil
    )
  }

  it should "should interpolate complex value" in {

    val environmentVariables = EnvironmentVariable("backend.environment_variables.url", None, Some("http://$backend1.host:$frontend.constants.const1/api/$$/$backend1.environment_variables.timeout")) ::
      EnvironmentVariable("backend1.environment_variables.timeout", None, Some("4000")) :: Nil

    resolveEnvironmentVariables(deployment(environmentVariables), DeploymentCluster("backend", Nil, None) :: Nil) should equal(
      EnvironmentVariable("backend.environment_variables.url", None, Some("http://$backend1.host:$frontend.constants.const1/api/$$/$backend1.environment_variables.timeout"), interpolated = Some("http://vamp.io:9050/api/$/4000")) ::
        EnvironmentVariable("backend1.environment_variables.timeout", None, Some("4000"), interpolated = None) :: Nil
    )
  }

  def deployment(environmentVariables: List[EnvironmentVariable]) = {
    val clusters = DeploymentCluster("backend1", Nil, None) :: DeploymentCluster("backend2", Nil, None) :: Nil
    val constants = Constant("frontend.constants.const1", None, Some("9050")) :: Constant("backend1.constants.const2", None, Some("$backend1.host")) :: Nil
    val hosts = Host("backend1.hosts.host", Some("vamp.io")) :: Nil
    Deployment("", clusters, Nil, Nil, environmentVariables, constants, hosts)
  }
}
