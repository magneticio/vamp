package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification.UnsupportedProtocolError
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.reader.ComposeWriter._

import scala.util.Try
import scala.language.higherKinds

/**
 * Pairs a result from a Yaml Compose Parse with a list of Comments
 * Comments are accumulated and later added to the Yaml as Yaml comments
 */
case class ComposeWriter[A](result: A, comments: List[String]) {

  def map[B](f: A ⇒ B): ComposeWriter[B] =
    ComposeWriter.map(this)(f)

  def flatMap[B](f: A ⇒ ComposeWriter[B]): ComposeWriter[B] =
    ComposeWriter.flatMap(this)(f)

  def run: (A, List[String]) = tupled(this)

}

object ComposeWriter {

  /** Extracts the comments from a ComposeWriter **/
  def extractComments[A](composeWriter: ComposeWriter[A]): List[String] = composeWriter.comments

  /** Creates a ComposeWriter from A without any comments **/
  def lift[A](a: A): ComposeWriter[A] = ComposeWriter(a, List())

  /** Retrieve the result from ComposeWriter **/
  def get[A](composeWriter: ComposeWriter[A]): A = composeWriter.result

  /** Change the result of ComposeWriter without touching the comments **/
  def map[A, B](composeWriter: ComposeWriter[A])(f: A ⇒ B): ComposeWriter[B] =
    composeWriter.copy(result = f(composeWriter.result))

  /** Sequence two ComposeWriter actions together **/
  def flatMap[A, B](composeWriter: ComposeWriter[A])(f: A ⇒ ComposeWriter[B]): ComposeWriter[B] =
    f(composeWriter.result) match {
      case cr: ComposeWriter[B] ⇒ cr.copy(comments = composeWriter.comments ++ cr.comments)
    }

  /** Extract the results of ComposeWriter in a tuple **/
  def tupled[A](composeWriter: ComposeWriter[A]): (A, List[String]) =
    (composeWriter.result, composeWriter.comments)

  /** Add comment to a parsed A **/
  def add[A](a: A)(addComment: A ⇒ String): ComposeWriter[A] =
    ComposeWriter(a, List(addComment(a)))

  def add[A](as: List[A])(addComments: List[A] ⇒ List[String]) =
    ComposeWriter(as, addComments(as))

  /** Or combinator first tries to first read if it fails it tries the second **/
  def or[A](read: YamlSourceReader ⇒ ComposeWriter[A])(readTwo: YamlSourceReader ⇒ ComposeWriter[A])(implicit source: YamlSourceReader): ComposeWriter[A] =
    Try(read(source)).getOrElse(readTwo(source))

  /** Sequences a List of ComposeWriter[A] to a ComposeWriter of List[A] **/
  def sequence[A](composeWriter: List[ComposeWriter[A]]): ComposeWriter[List[A]] =
    ComposeWriter(composeWriter.map(get), composeWriter.flatMap(extractComments))

}

/**
 * Reads a Blueprint from a docker-compose yaml
 */
object ComposeBlueprintReader extends YamlReader[ComposeWriter[Blueprint]] {

  /**
   * Adds the name to the defined blueprints
   * Use this function instead of parse, since name is not available in YamlReader.parse
   */
  def fromDockerCompose(name: String)(implicit source: String): ComposeWriter[Blueprint] =
    this.read(source).map {
      case defaultBlueprint: DefaultBlueprint ⇒ defaultBlueprint.copy(name = name)
      case other                              ⇒ other
    }

  /**
   * Flattens and comments unnused values from the docker-compose yaml
   */
  private def flattenUnusedValues(implicit source: YamlSourceReader): ComposeWriter[Unit] =
    sequence(source.flattenNotConsumed().toList.map {
      case (key, value) ⇒
        add(())(_ ⇒ s"Did not include unsupported field: '$key' with value: '$value'")
    }).map(_ ⇒ source.flatten())

  override protected def parse(implicit source: YamlSourceReader): ComposeWriter[Blueprint] =
    for {
      _ ← add(<<![String]("version"))(v ⇒ s"Compose version: $v.")
      clusters ← ComposeClusterReader.read
      _ ← flattenUnusedValues
    } yield DefaultBlueprint(
      name = "", // will be replaced by fromDockerCompose function
      metadata = Map(),
      clusters = clusters,
      gateways = List(),
      environmentVariables = List(),
      dialects = Map())

}

object ComposeClusterReader extends YamlReader[ComposeWriter[List[Cluster]]] {

  override protected def parse(implicit source: YamlSourceReader): ComposeWriter[List[Cluster]] =
    for {
      clusters ← parseClusters
      clustersWithDependencies ← resolveDependencies(clusters, clusters.map(c ⇒ c.name))
      clustersWithDependenciesAndPorts ← addPorts(clustersWithDependencies.map(_._1), clustersWithDependencies.flatMap(_._2))
    } yield clustersWithDependenciesAndPorts

  private def parseClusters(implicit source: YamlSourceReader): ComposeWriter[List[Cluster]] =
    sequence(<<?[YamlSourceReader]("services") match {
      case Some(yaml) ⇒ yaml
        .pull()
        .toList
        .flatMap {
          case (name: String, yaml: YamlSourceReader) ⇒
            Some(ComposeServicesReader.parseService(name)(yaml).map { service ⇒
              Cluster(
                name = name,
                metadata = Map(),
                services = List(service),
                gateways = List(),
                healthChecks = None,
                network = None,
                sla = None,
                dialects = Map())
            })
          case _ ⇒ None
        }
      case None ⇒ List()
    })

  private def getBreedField[F[_], A](breed: Breed)(default: F[A])(fromBreed: DefaultBreed ⇒ F[A]): F[A] =
    breed match {
      case defaultBreed: DefaultBreed ⇒ fromBreed(defaultBreed)
      case _                          ⇒ default
    }

  private def setBreedValue(setValue: DefaultBreed ⇒ Breed)(breed: Breed): Breed =
    breed match {
      case defaultBreed: DefaultBreed ⇒ setValue(defaultBreed)
      case _                          ⇒ breed
    }

  private def replaceValueOpt(clusterNames: List[String])(environmentVariable: EnvironmentVariable) =
    environmentVariable
      .value
      .flatMap { (environmentValue: String) ⇒
        clusterNames.map(cn ⇒ cn → s"($cn):(\\d+)".r).flatMap {
          case (cn, pattern) ⇒
            environmentValue match {
              case pattern(name, port) ⇒ Some(Tuple3(
                cn,
                environmentVariable.copy(value = Some("$" + cn + ".host:" + "$" + cn + ".ports.port_" + port)),
                port))
              case xs ⇒ None
            }
        }.headOption
      }

  private def resolveDependencies(clusters: List[Cluster], clusterNames: List[String]): ComposeWriter[List[(Cluster, List[(String, String)])]] =
    clusters.foldRight(lift(List.empty[(Cluster, List[(String, String)])])) { (cluster, acc) ⇒
      acc.flatMap { clusters ⇒
        addEnvironmentVariables(cluster.services.head, clusterNames).map {
          case (service, portsWithCluster) ⇒
            (cluster.copy(services = List(service)) → portsWithCluster) +: clusters
        }
      }
    }

  private def addEnvironmentVariables(service: Service, clusterNames: List[String]): ComposeWriter[(Service, List[(String, String)])] =
    getBreedField[List, EnvironmentVariable](service.breed)(List())(_.environmentVariables)
      .flatMap(ev ⇒ replaceValueOpt(clusterNames)(ev))
      .foldRight(lift(service → List.empty[(String, String)])) {
        case ((cn, env, port), serviceWriter) ⇒
          serviceWriter.flatMap {
            case (s, portsWithCluster) ⇒
              val newEnvironmentVars = getBreedField[List, EnvironmentVariable](s.breed)(List())(_.environmentVariables)
                .foldRight(List.empty[EnvironmentVariable]) { (ev, acc) ⇒
                  if (ev.name == env.name) env +: acc
                  else ev +: acc
                }

              val newDependencies: Map[String, Breed] =
                getBreedField[({ type F[A] = Map[String, A] })#F, Breed](service.breed)(Map())(_.dependencies) +
                  (cn → BreedReference(s"$cn:1.0.0"))

              add(service.copy(
                breed = setBreedValue(_.copy(
                  environmentVariables = newEnvironmentVars,
                  dependencies = newDependencies))(s.breed)) → ((cn, port) +: portsWithCluster)
              ) { _ ⇒
                s"Created port reference in environment variable: '${env.name}'"
              }
          }
      }

  private def addPorts(clusters: List[Cluster], portsWithCluster: List[(String, String)]): ComposeWriter[List[Cluster]] =
    clusters.foldRight(lift(List.empty[Cluster])) { (cluster, acc) ⇒
      acc.flatMap { clusters ⇒
        portsWithCluster
          .filter(_._1 == cluster.name)
          .foldRight(lift(cluster)) {
            case ((cn, port), clusterWriter) ⇒
              clusterWriter.flatMap { c: Cluster ⇒
                val newService = c.services
                  .head
                  .copy(
                    breed = setBreedValue(b ⇒
                      b.copy(
                        ports = b.ports :+ Port(s"port_$port", None, Some(port), port.toInt, Port.Type.Http))
                    )(c.services.head.breed))

                add(cluster.copy(services = List(newService)))(_ ⇒ s"Created port: 'port_$port' in breed: '$cn:1.0.0'.")
              }
          }.map { newCluster ⇒
            newCluster +: clusters
          }
      }
    }
}

/**
 * Reads a a services from a docker-compose yaml
 */
object ComposeServicesReader extends YamlReader[ComposeWriter[Service]] {

  def parseService(name: String)(yaml: YamlSourceReader): ComposeWriter[Service] =
    parse(yaml).map { service ⇒
      service.copy(breed = service.breed match {
        case defaultBreed: DefaultBreed ⇒ defaultBreed.copy(name = s"$name:1.0.0")
      })
    }

  protected def parse(implicit yaml: YamlSourceReader): ComposeWriter[Service] =
    for {
      deployable ← lift(Deployable(Some("container/docker"), <<![String]("image")))
      ports ← ComposePortReader.read
      dialects ← lift(<<?[String]("command").map(c ⇒ Map("docker" → c)).getOrElse(Map()))
      dependencies ← lift(<<?[List[String]]("depends_on").getOrElse(List()).map(d ⇒ d → BreedReference(s"$d:1.0.0")).toMap)
      environmentVariables ← or(s ⇒ ComposeEnvironmentReaderList.read(s))(s ⇒ ComposeEnvironmentReaderMap.read(s))
    } yield Service(
      breed = DefaultBreed(
        name = "",
        metadata = Map(),
        deployable = deployable,
        ports = ports,
        environmentVariables = environmentVariables,
        constants = List(),
        arguments = List(),
        dependencies = dependencies,
        healthChecks = None),
      environmentVariables = List(),
      scale = None,
      arguments = List(),
      healthChecks = None,
      network = None,
      dialects = dialects,
      health = None)

}

/**
 * Reads a list of ports from a docker-compose yaml
 */
object ComposePortReader extends YamlReader[ComposeWriter[List[Port]]] {

  override protected def parse(implicit source: YamlSourceReader): ComposeWriter[List[Port]] =
    sequence(<<?[List[String]]("ports").map { ports ⇒
      ports.map { portString ⇒
        val composePort = portString.split(":") match {
          case Array(singlePort) ⇒ lift(parsePort(singlePort))
          case Array(hostPort, containerPort) ⇒
            add(parsePort(containerPort))(_ ⇒ s"Ignored host port: $hostPort.")
          case Array(ip, hostPort, containerPort) ⇒
            add(parsePort(containerPort))(_ ⇒ s"Ignored ip: $ip and host port: $hostPort.")
        }

        composePort.flatMap {
          case SinglePort(port)      ⇒ lift(Port(s"port_$port", None, Some(port.toString), port, Port.Type.Http))
          case PortRange(start, end) ⇒ add(Port(s"port_$start", None, Some(start.toString), start, Port.Type.Http))(_ ⇒ s"Ignored port ranges to: $end.")
          case PortWithType(port, pt) ⇒
            val portType = if (pt.equalsIgnoreCase("tcp")) Port.Type.Tcp
            else if (pt.equalsIgnoreCase("http")) Port.Type.Http
            else throwException(UnsupportedProtocolError(pt))

            lift(Port(s"port_$port", None, Some(port.toString), port, portType))
        }
      }
    }.getOrElse(List()))

  // Private ADT for differentiating types of ports
  private sealed trait ComposePort
  private case class SinglePort(port: Int) extends ComposePort
  private case class PortRange(start: Int, end: Int) extends ComposePort
  private case class PortWithType(port: Int, portType: String) extends ComposePort

  private def parsePort(portString: String): ComposePort = portString.split("-") match {
    case Array(port)               ⇒ portType(port)
    case Array(startPort, endPort) ⇒ PortRange(startPort.toInt, endPort.toInt)
  }

  private def portType(s: String): ComposePort = s.split("/") match {
    case Array(port)           ⇒ SinglePort(port.toInt)
    case Array(port, portType) ⇒ PortWithType(port.toInt, portType)
  }

}

/**
 * Reads a list of environment values that are defined in the docker-compose yaml as a list
 */
object ComposeEnvironmentReaderList extends YamlReader[ComposeWriter[List[EnvironmentVariable]]] {

  override protected def parse(implicit source: YamlSourceReader): ComposeWriter[List[EnvironmentVariable]] =
    lift(<<?[List[String]]("environment").map { environments ⇒
      environments.map(_.split("=") match {
        case Array(key, value) ⇒ EnvironmentVariable(key, None, Some(value), None)
      })
    }.getOrElse(List()))

}

/**
 * Reads a list of environment values that are defined in the docker-compose yaml as key-value pairs
 */
object ComposeEnvironmentReaderMap extends YamlReader[ComposeWriter[List[EnvironmentVariable]]] {

  override protected def parse(implicit source: YamlSourceReader): ComposeWriter[List[EnvironmentVariable]] =
    lift(<<?[YamlSourceReader]("environment")
      .map {
        _.pull()
          .toList
          .map {
            case (key, value) ⇒
              EnvironmentVariable(key, None, Some(value.asInstanceOf[String]), None)
          }
      }.getOrElse(List()))

}

