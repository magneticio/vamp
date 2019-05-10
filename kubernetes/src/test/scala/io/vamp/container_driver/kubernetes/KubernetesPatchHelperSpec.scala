package io.vamp.container_driver.kubernetes

import java.nio.charset.Charset

import com.squareup.okhttp.Request
import io.kubernetes.client.ApiClient
import okio.Buffer
import org.json4s._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class KubernetesPatchHelperSpec extends FlatSpec with Matchers {

  implicit val formats: Formats = DefaultFormats

  "KubernetesPatchHelper" should "prepare correct deployment patch request" in {

    val body =
      """
        |{
        |	"metadata": {
        |		"name": "vamp-gateway-agent"
        |	},
        |	"spec": {
        |		"replicas": 2,
        |		"template": {
        |			"spec": {
        |				"containers": [
        |					{
        |						"name": "vamp-gateway-agent",
        |						"image": "magneticio/vamp-gateway-agent:ci-671-VE-598",
        |						"securityContext": {
        |							"privileged": true
        |						}
        |					}
        |				]
        |			},
        |			"metadata": {
        |				"labels": {
        |					"io.vamp": "vamp-gateway-agent"
        |				}
        |			}
        |		}
        |	},
        |	"kind": "Deployment",
        |	"apiVersion": "extensions/v1beta1"
        |}
      """.stripMargin

    val actual = KubernetesPatchHelper.prepareDeploymentPatchRequest(body, new ApiClient(), "customNamespace")

    actual.headers().get("Content-Type") should be("application/merge-patch+json")
    actual.url().toString should be("https://localhost/apis/extensions/v1beta1/namespaces/customNamespace/deployments/vamp-gateway-agent")
    actual.method() should be("PATCH")

    val actualBody: String = getBody(actual)
    actualBody should be(body.replaceAll("\\s", ""))
  }

  it should "prepare correct daemonSet patch request" in {

    val body =
      """
        |{
        | "metadata": {
        |		"name": "vamp-daemon"
        |	},
        | "apiVersion": "extensions/v1beta1",
        | "kind": "DaemonSet"
        |}
        |""".stripMargin

    val actual = KubernetesPatchHelper.prepareDaemonSetPatchRequest(body, new ApiClient(), "customNamespace")

    actual.headers().get("Content-Type") should be("application/merge-patch+json")
    actual.url().toString should be("https://localhost/apis/extensions/v1beta1/namespaces/customNamespace/daemonsets/vamp-daemon")
    actual.method() should be("PATCH")

    val actualBody = getBody(actual)
    actualBody should be(body.replaceAll("\\s", ""))
  }

  it should "prepare correct service patch request" in {

    val body =
      """
        |{
        |	"metadata": {
        |		"name": "vamp-service"
        |	},
        |	"kind": "Service",
        |	"apiVersion": "extensions/v1"
        |}
      """.stripMargin

    val actual = KubernetesPatchHelper.prepareServicePatchRequest(body, new ApiClient(), "customNamespace")

    actual.headers().get("Content-Type") should be("application/merge-patch+json")
    actual.url().toString should be("https://localhost/api/v1/namespaces/customNamespace/services/vamp-service")
    actual.method() should be("PATCH")

    val actualBody = getBody(actual)
    actualBody should be(body.replaceAll("\\s", ""))
  }

  private def getBody(actual: Request): String = {
    val buffer = new Buffer()
    actual.body().writeTo(buffer)
    buffer.readString(Charset.defaultCharset()).replaceAll("\\s", "")
  }
}
