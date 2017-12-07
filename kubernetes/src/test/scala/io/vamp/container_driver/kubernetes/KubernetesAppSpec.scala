package io.vamp.container_driver.kubernetes

import io.vamp.container_driver.{ Docker, DockerPortMapping }
import org.json4s._
import org.json4s.native.Serialization._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class KubernetesAppSpec extends FlatSpec with Matchers {

  implicit val formats: Formats = DefaultFormats

  "KubernetesApp" should "marshall to string" in {
    val app = KubernetesApp(
      name = "my_app",
      docker = Docker("may/app", List(DockerPortMapping(8080, Some(80))), Nil, privileged = true, network = "custom"),
      replicas = 3,
      cpu = 1,
      mem = 1024,
      privileged = true,
      env = Map[String, String]("HOME" → "/usr/local/app", "BIN" → "/a/b"),
      cmd = List("a", "b"),
      args = List("arg"),
      labels = Map[String, String]("node" → "test")
    )

    val actual = read[Any](app.toString)
    val expected = read[Any](
      """
          |{
          |  "apiVersion": "extensions/v1beta1",
          |  "kind": "Deployment",
          |  "metadata": {
          |   "name": "my_app"
          |  },
          |  "spec": {
          |   "replicas": 3,
          |   "template": {
          |     "metadata": {
          |       "labels": {"node": "test"}
          |     },
          |     "spec": {
          |       "containers": [{
          |         "image": "may/app",
          |         "name": "my_app",
          |         "env": [{"name": "HOME", "value": "/usr/local/app"}, {"name": "BIN", "value": "/a/b"}],
          |         "ports": [{"name": "p8080", "containerPort": 8080, "protocol": "TCP"}],
          |         "args": ["arg"],
          |         "command": ["a", "b"],
          |         "resources": {
          |           "requests": {
          |             "cpu": 1.0,
          |             "memory": "1024Mi"
          |           }
          |         },
          |         "securityContext": {
          |           "privileged": true
          |         }
          |       }]
          |     }
          |   }
          |  }
          |}
          |""".stripMargin
    )

    actual should be(expected)
  }

  it should "merge dialect data" in {
    val app = KubernetesApp(
      name = "my_app",
      docker = Docker("may/app", List(DockerPortMapping(8080, Some(80))), Nil, privileged = false, network = "custom"),
      replicas = 3,
      cpu = 1,
      mem = 1024,
      privileged = false,
      env = Map[String, String]("HOME" → "/usr/local/app", "BIN" → "/a/b"),
      cmd = List("a", "b"),
      args = List("arg"),
      labels = Map[String, String]("node" → "test"),
      dialect = read[Any](
        """
          |{
          |  "affinity": {
          |    "podAntiAffinity": {
          |      "requiredDuringSchedulingIgnoredDuringExecution": [
          |        {
          |          "labelSelector": {
          |            "matchExpressions": [
          |              {
          |                "key": "app",
          |                "operator": "In",
          |                "values": [
          |                  "store"
          |                ]
          |              }
          |            ]
          |          },
          |          "topologyKey": "kubernetes.io/hostname"
          |        }
          |      ]
          |    }
          |  },
          |  "dnsPolicy": "ClusterFirst",
          |  "nodeName": "aci-connector"
          |}
        """.stripMargin
      ).asInstanceOf[Map[String, Any]]
    )

    val actual = read[Any](app.toString)
    val expected = read[Any](
      """
          |{
          |  "apiVersion": "extensions/v1beta1",
          |  "kind": "Deployment",
          |  "metadata": {
          |   "name": "my_app"
          |  },
          |  "spec": {
          |   "replicas": 3,
          |   "template": {
          |     "metadata": {
          |       "labels": {"node": "test"}
          |     },
          |     "spec": {
          |       "containers": [{
          |         "image": "may/app",
          |         "name": "my_app",
          |         "env": [{"name": "HOME", "value": "/usr/local/app"}, {"name": "BIN", "value": "/a/b"}],
          |         "ports": [{"name": "p8080", "containerPort": 8080, "protocol": "TCP"}],
          |         "args": ["arg"],
          |         "command": ["a", "b"],
          |         "resources": {
          |           "requests": {
          |             "cpu": 1.0,
          |             "memory": "1024Mi"
          |           }
          |         },
          |         "securityContext": {
          |           "privileged": false
          |         }
          |       }],
          |       "affinity": {
          |         "podAntiAffinity": {
          |           "requiredDuringSchedulingIgnoredDuringExecution": [
          |             {
          |               "labelSelector": {
          |                 "matchExpressions": [
          |                   {
          |                     "key": "app",
          |                     "operator": "In",
          |                     "values": [
          |                       "store"
          |                     ]
          |                   }
          |                 ]
          |               },
          |               "topologyKey": "kubernetes.io/hostname"
          |             }
          |           ]
          |         }
          |       },
          |       "dnsPolicy": "ClusterFirst",
          |       "nodeName": "aci-connector"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
    )

    actual should be(expected)
  }

  it should "override dialect container" in {
    val app = KubernetesApp(
      name = "my_app",
      docker = Docker("may/app", List(DockerPortMapping(8080, Some(80))), Nil, privileged = true, network = "custom"),
      replicas = 3,
      cpu = 1,
      mem = 1024,
      privileged = true,
      env = Map[String, String]("HOME" → "/usr/local/app"),
      cmd = List("a", "b"),
      args = List("arg"),
      labels = Map[String, String]("node" → "test"),
      dialect = read[Any](
        """
          |{
          |  "containers": [{
          |    "image": "malicious",
          |    "name": "malicious"
          |  }],
          |  "dnsPolicy": "ClusterFirst",
          |  "nodeName": "aci-connector"
          |}
        """.stripMargin
      ).asInstanceOf[Map[String, Any]]
    )

    val actual = read[Any](app.toString)
    val expected = read[Any](
      """
          |{
          |  "apiVersion": "extensions/v1beta1",
          |  "kind": "Deployment",
          |  "metadata": {
          |   "name": "my_app"
          |  },
          |  "spec": {
          |   "replicas": 3,
          |   "template": {
          |     "metadata": {
          |       "labels": {"node": "test"}
          |     },
          |     "spec": {
          |       "containers": [{
          |         "image": "may/app",
          |         "name": "my_app",
          |         "env": [{"name": "HOME", "value": "/usr/local/app"}],
          |         "ports": [{"name": "p8080", "containerPort": 8080, "protocol": "TCP"}],
          |         "args": ["arg"],
          |         "command": ["a", "b"],
          |         "resources": {
          |           "requests": {
          |             "cpu": 1.0,
          |             "memory": "1024Mi"
          |           }
          |         },
          |         "securityContext": {
          |           "privileged": true
          |         }
          |       }],
          |       "dnsPolicy": "ClusterFirst",
          |       "nodeName": "aci-connector"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
    )

    actual should be(expected)
  }

  it should "merge dialect container" in {
    val app = KubernetesApp(
      name = "my_app",
      docker = Docker("may/app", List(DockerPortMapping(8080, Some(80))), Nil, privileged = true, network = "custom"),
      replicas = 3,
      cpu = 1,
      mem = 1024,
      privileged = true,
      env = Map[String, String]("HOME" → "/usr/local/app"),
      cmd = List("a", "b"),
      args = List("arg"),
      labels = Map[String, String]("node" → "test"),
      dialect = read[Any](
        """
          |{
          |  "containers": [{
          |    "volumeMounts": [{
          |      "name": "azure",
          |      "mountPath": "/mnt/azure"
          |    }]
          |  }],
          |  "volumes": [{
          |    "name": "azure",
          |    "azureDisk": {
          |      "diskName": "test.vhd",
          |      "diskURI": "https://someaccount.blob.microsoft.net/vhds/test.vhd"
          |    }
          |  }]
          |}
        """.stripMargin
      ).asInstanceOf[Map[String, Any]]
    )

    val actual = read[Any](app.toString)
    val expected = read[Any](
      """
        |{
        |  "apiVersion": "extensions/v1beta1",
        |  "kind": "Deployment",
        |  "metadata": {
        |   "name": "my_app"
        |  },
        |  "spec": {
        |   "replicas": 3,
        |   "template": {
        |     "metadata": {
        |       "labels": {"node": "test"}
        |     },
        |     "spec": {
        |       "containers": [{
        |         "image": "may/app",
        |         "name": "my_app",
        |         "env": [{"name": "HOME", "value": "/usr/local/app"}],
        |         "ports": [{"name": "p8080", "containerPort": 8080, "protocol": "TCP"}],
        |         "args": ["arg"],
        |         "command": ["a", "b"],
        |         "resources": {
        |           "requests": {
        |             "cpu": 1.0,
        |             "memory": "1024Mi"
        |           }
        |         },
        |         "securityContext": {
        |           "privileged": true
        |         },
        |         "volumeMounts": [{
        |           "name": "azure",
        |           "mountPath": "/mnt/azure"
        |         }]
        |       }],
        |       "volumes": [
        |         {
        |           "name": "azure",
        |           "azureDisk": {
        |             "diskName": "test.vhd",
        |             "diskURI": "https://someaccount.blob.microsoft.net/vhds/test.vhd"
        |           }
        |         }
        |       ]
        |      }
        |    }
        |  }
        |}
        |""".stripMargin
    )

    actual should be(expected)
  }
}
