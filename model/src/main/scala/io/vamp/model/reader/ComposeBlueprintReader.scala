package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification.UnsupportedProtocolError
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.reader.ComposeReader._

import scala.util.Try

/**
  * Pairs a result from a Yaml Compose Parse with a list of Comments
  * Comments are accumulated and later added to the Yaml as Yaml comments
  */
case class ComposeReader[A](result: A, comments: List[String]) {

  def map[B](f: A => B): ComposeReader[B] =
    ComposeReader.map(this)(f)

  def flatMap[B](f: A => ComposeReader[B]): ComposeReader[B] =
    ComposeReader.flatMap(this)(f)

}

object ComposeReader {

  /** Extracts the comments from a ComposReader **/
  def extractComments[A](composeReader: ComposeReader[A]): List[String] = composeReader.comments

  /** Creates a ComposeReader from A without any comments **/
  def lift[A](a: A): ComposeReader[A] = ComposeReader(a, List())

  /** Retrieve the result from ComposeReader **/
  def get[A](composeReader: ComposeReader[A]): A = composeReader.result

  /** Change the result of ComposeReader without touching the comments **/
  def map[A, B](composeReader: ComposeReader[A])(f: A => B): ComposeReader[B] =
    composeReader.copy(result = f(composeReader.result))

  /** Sequence two composeReader actions together **/
  def flatMap[A, B](composeReader: ComposeReader[A])(f: A => ComposeReader[B]): ComposeReader[B] =
    f(composeReader.result) match {
      case cr: ComposeReader[B] => cr.copy(comments = composeReader.comments ++ cr.comments)
    }

  /** Extract the results of ComposeReader in a tuple **/
  def tupled[A](composeReader: ComposeReader[A]): (A, List[String]) =
    (composeReader.result, composeReader.comments)

  /** Add comment to a parsed A **/
  def add[A](a: A)(addComment: A => String): ComposeReader[A] =
    ComposeReader(a, List(addComment(a)))

  def add[A](as: List[A])(addComments: List[A] => List[String]) =
    ComposeReader(as, addComments(as))

  /** Or combinator first tries to first read if it fails it tries the second **/
  def or[A](read: YamlSourceReader => ComposeReader[A])
           (readTwo: YamlSourceReader => ComposeReader[A])
           (implicit source: YamlSourceReader): ComposeReader[A] =
    Try(read(source)).getOrElse(readTwo(source))

  /** Sequences a List of ComposeReaders[A] to a ComposeReader of List[A] **/
  def sequence[A](composeReaders: List[ComposeReader[A]]): ComposeReader[List[A]] =
    ComposeReader(composeReaders.map(get), composeReaders.flatMap(extractComments))

}

/**
  * Reads a Blueprint from a docker-compose yaml
  */
object ComposeBlueprintReader extends YamlReader[ComposeReader[Blueprint]] {

  /**
    * Adds the name to the defined blueprints
    * Use this function instead of parse, since name is not available in YamlReader.parse
    */
  def fromDockerCompose(name: String)(implicit source: String): ComposeReader[Blueprint] =
    this.read(source).map {
      case defaultBlueprint: DefaultBlueprint => defaultBlueprint.copy(name = name)
      case other => other
    }

  override protected def parse(implicit source: YamlSourceReader): ComposeReader[Blueprint] = {
    for {
      _        <- add(<<![String]("version"))(v => s"Compose version: $v.")
      _        <- add(<<?[YamlSourceReader]("volumes").map(_.pull()))(_ => "Found not supported volume definitions.")
      clusters <- ComposeClusterReader.read
    } yield {

      DefaultBlueprint(
        name = "", // will be replaced by fromDockerCompose function
        metadata = Map(),
        clusters = clusters,
        gateways = List(),
        environmentVariables = List(),
        dialects = Map())
    }
  }

}

object ComposeClusterReader extends YamlReader[ComposeReader[List[Cluster]]] {
  override protected def parse(implicit source: YamlSourceReader): ComposeReader[List[Cluster]] =
    sequence(<<?[YamlSourceReader]("services") match {
      case Some(yaml) => yaml
        .pull()
        .toList
        .flatMap {
          case (name: String, yaml: YamlSourceReader) =>
            Some(ComposeServicesReader.parseService(name)(yaml).map { service =>
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
          case _ => None
        }
      case None => List()
    })
}

/**
  * Reads a a services from a docker-compose yaml
  */
object ComposeServicesReader extends YamlReader[ComposeReader[Service]] {

  def parseService(name: String)(yaml: YamlSourceReader): ComposeReader[Service] =
    parse(yaml).map { service =>
      service.copy(breed = service.breed match {
        case defaultBreed: DefaultBreed => defaultBreed.copy(name = s"$name:1.0.0")
      })
    }

  protected def parse(implicit yaml: YamlSourceReader): ComposeReader[Service] = {
    for {
      deployable   <- lift(Deployable(Some("container/docker"), <<![String]("image")))
      ports        <- ComposePortReader.read
      dialects     <- lift(<<?[String]("command").map(c => Map("docker" -> c)).getOrElse(Map()))
      dependencies <- lift(<<?[List[String]]("depends_on").getOrElse(List()).map(d => d -> BreedReference(s"$d:1.0.0")).toMap)
      _            <- add(<<?[String]("restart"))(s => s"Ignored restart value: $s.")
      _            <- add(<<?[List[String]]("volumes").getOrElse(List()))(_.map(v => s"Ignored not supported volume: $v."))
      environmentVariables <- or(s => ComposeEnvironmentReaderList.read(s))(s => ComposeEnvironmentReaderMap.read(s))
    } yield {
      val breed = DefaultBreed(
        name = "",
        metadata = Map(),
        deployable = deployable,
        ports = ports,
        environmentVariables = environmentVariables,
        constants = List(),
        arguments = List(),
        dependencies = dependencies,
        healthChecks = None)

      Service(
        breed = breed,
        environmentVariables = List(),
        scale = None,
        arguments = List(),
        healthChecks = None,
        network = None,
        dialects = dialects,
        health = None)
    }
  }

}

/**
  * Reads a list of ports from a docker-compose yaml
  */
object ComposePortReader extends YamlReader[ComposeReader[List[Port]]] {

  override protected def parse(implicit source: YamlSourceReader): ComposeReader[List[Port]] =
    sequence(<<?[List[String]]("ports").map { ports =>
      ports.map { portString =>
        val composePort = portString.split(":") match {
          case Array(singlePort) =>lift(parsePort(singlePort))
          case Array(hostPort, containerPort) =>
            add(parsePort(containerPort))(_ => s"Ignored host port: $hostPort.")
          case Array(ip, hostPort, containerPort) =>
            add(parsePort(containerPort))(_ => s"Ignored ip: $ip and host port: $hostPort.")
        }

        composePort.flatMap {
          case SinglePort(port) => lift(Port(port))
          case PortRange(start, end) => add(Port(start))(_ => s"Ignored port ranges to: $end.")
          case PortWithType(port, pt) =>
            val portType = if (pt.equalsIgnoreCase("tcp")) Port.Type.Tcp
            else if (pt.equalsIgnoreCase("http")) Port.Type.Http
            else throwException(UnsupportedProtocolError(pt))

            lift(Port(port, portType))
        }
      }
    }.getOrElse(List()))

  // Private ADT for differentiating types of ports
  private sealed trait ComposePort
  private case class SinglePort(port: Int) extends ComposePort
  private case class PortRange(start: Int, end: Int) extends ComposePort
  private case class PortWithType(port: Int, portType: String) extends ComposePort

  private def parsePort(portString: String): ComposePort = portString.split("-") match {
    case Array(port) => portType(port)
    case Array(startPort, endPort) => PortRange(startPort.toInt, endPort.toInt)
  }

  private def portType(s: String): ComposePort = s.split("/") match {
    case Array(port) => SinglePort(port.toInt)
    case Array(port, portType) => PortWithType(port.toInt, portType)
  }

}

/**
  * Reads a list of environment values that are defined in the docker-compose yaml as a list
  */
object ComposeEnvironmentReaderList extends YamlReader[ComposeReader[List[EnvironmentVariable]]] {

  override protected def parse(implicit source: YamlSourceReader):  ComposeReader[List[EnvironmentVariable]] =
    lift(<<?[List[String]]("environment").map { environments =>
      environments.map(_.split("=") match {
        case Array(key, value) => EnvironmentVariable(key, None, Some(value), None)
      })
    }.getOrElse(List()))

}

/**
  * Reads a list of environment values that are defined in the docker-compose yaml as key-value pairs
  */
object ComposeEnvironmentReaderMap extends YamlReader[ComposeReader[List[EnvironmentVariable]]] {

  override protected def parse(implicit source: YamlSourceReader): ComposeReader[List[EnvironmentVariable]] =
    lift(<<?[YamlSourceReader]("environment")
      .map {
        _.pull()
          .toList
          .map { case (key, value) =>
            EnvironmentVariable(key, None, Some(value.asInstanceOf[String]), None)
          }
      }.getOrElse(List()))

}

