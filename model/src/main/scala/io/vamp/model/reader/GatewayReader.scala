package io.vamp.model.reader

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._

import scala.language.postfixOps

trait AbstractGatewayReader extends YamlReader[Gateway] with AnonymousYamlReader[Gateway] with GatewayRouteValidation {

  private val nameMatcher = """^[^\s\[\]]+$""".r

  override protected def expand(implicit source: YamlSourceReader) = {
    <<?[Any]("routes") match {
      case Some(route: String) ⇒ >>("routes" :: route :: Nil, YamlSourceReader())
      case Some(routes: List[_]) ⇒ routes.foreach {
        case route: String           ⇒ >>("routes" :: route :: Nil, YamlSourceReader())
        case route: YamlSourceReader ⇒ route.pull().foreach { case (name, value) ⇒ >>("routes" :: name :: Nil, value) }
        case _                       ⇒
      }
      case _ ⇒
    }
    <<?[Any]("virtual_hosts") match {
      case Some(host: String) ⇒ >>("virtual_hosts" :: Nil, List(host))
      case Some(_: List[_])   ⇒
      case Some(any)          ⇒ >>("virtual_hosts" :: Nil, List(any))
      case None               ⇒
    }
    super.expand
  }

  override protected def parse(implicit source: YamlSourceReader): Gateway = {
    source.find[String]("internal")
    Gateway(name, port, service, sticky, virtualHosts, routes(splitPath = true), deployed)
  }

  protected def port(implicit source: YamlSourceReader): Port = <<![Any]("port") match {
    case value: Int    ⇒ Port(value)
    case value: String ⇒ Port(value)
    case any           ⇒ throwException(UnexpectedTypeError("port", classOf[String], any.getClass))
  }

  protected def service(implicit source: YamlSourceReader): Option[GatewayService] = <<?[YamlSourceReader]("service").map { _ ⇒
    val host = <<![String]("service" :: "host")
    val port = <<![Any]("service" :: "port") match {
      case value: Int ⇒ Port(value)
      case value      ⇒ Port(value.toString)
    }
    GatewayService(host, port)
  }

  protected def sticky(implicit source: YamlSourceReader) = <<?[String]("sticky") match {
    case Some("none") ⇒ None
    case Some(sticky) ⇒ Option(Gateway.Sticky.byName(sticky).getOrElse(throwException(IllegalGatewayStickyValue(sticky))))
    case None         ⇒ None
  }

  protected def virtualHosts(implicit source: YamlSourceReader): List[String] = <<?[List[_]]("virtual_hosts") map {
    _.map {
      case host: String ⇒ host
      case any          ⇒ throwException(IllegalGatewayVirtualHosts)
    }
  } getOrElse Nil

  protected def routes(splitPath: Boolean)(implicit source: YamlSourceReader): List[Route] = <<?[YamlSourceReader]("routes") map {
    _.pull().map {
      case (name: String, _) ⇒ RouteReader.readReferenceOrAnonymous(<<![Any]("routes" :: name :: Nil)) match {
        case route: DefaultRoute   ⇒ route.copy(path = if (splitPath) name else GatewayPath(name :: Nil))
        case route: RouteReference ⇒ route.copy(path = if (splitPath) name else GatewayPath(name :: Nil))
        case route                 ⇒ route
      }
    } toList
  } getOrElse Nil

  protected def deployed(implicit source: YamlSourceReader): Boolean = <<?[Boolean]("deployed").getOrElse(false)

  override protected def validate(gateway: Gateway): Gateway = {

    gateway.routes.foreach(route ⇒ if (route.length < 1 || route.length > 4) throwException(UnsupportedRoutePathError(route.path)))

    if (gateway.port.`type` != Port.Type.Http && gateway.sticky.isDefined) throwException(StickyPortTypeError(gateway.port.copy(name = gateway.port.value.get)))

    (validateGatewayRouteWeights andThen validateGatewayRouteConditionStrengths)(gateway)
  }

  override def validateName(name: String): String = {

    def error = throwException(UnsupportedGatewayNameError(name))

    name match {
      case nameMatcher(_*) ⇒ GatewayPath(name).segments match {
        case path ⇒ if (path.size < 1 || path.size > 4) error
      }
      case _ ⇒ error
    }

    name
  }
}

object GatewayReader extends AbstractGatewayReader

object ClusterGatewayReader extends AbstractGatewayReader {
  override protected def parse(implicit source: YamlSourceReader): Gateway = Gateway(name, Port(<<![String]("port"), None, None), service, sticky, virtualHosts, routes(splitPath = false), deployed)
}

object DeployedGatewayReader extends AbstractGatewayReader {

  protected override def name(implicit source: YamlSourceReader): String = <<?[String]("name") match {
    case None       ⇒ AnonymousYamlReader.name
    case Some(name) ⇒ name
  }

  protected override def port(implicit source: YamlSourceReader): Port = <<?[Any]("port") match {
    case Some(value: Int)    ⇒ Port(value)
    case Some(value: String) ⇒ Port(value)
    case _                   ⇒ Port("", None, None)
  }
}

object RouteReader extends YamlReader[Route] with WeakReferenceYamlReader[Route] {

  import YamlSourceReader._

  override protected def createReference(implicit source: YamlSourceReader): Route = RouteReference(reference, Route.noPath)

  override protected def createDefault(implicit source: YamlSourceReader): Route = {
    source.find[String](Lookup.entry)
    source.flatten({ entry ⇒ entry == "targets" })
    DefaultRoute(name, Route.noPath, <<?[Percentage]("weight"), condition, <<?[Percentage]("condition_strength"), rewrites, balance)
  }

  override protected def expand(implicit source: YamlSourceReader) = {

    def list(name: String) = <<?[Any](name) match {
      case Some(s: String)     ⇒ expandToList(name)
      case Some(list: List[_]) ⇒
      case Some(m)             ⇒ >>(name, List(m))
      case _                   ⇒
    }

    <<?[Any]("condition") collect {
      case condition: String ⇒ if (!condition.isEmpty) >>("condition", YamlSourceReader(Map("condition" → condition))) else >>("condition", None)
      case yaml: YamlSourceReader if yaml.find[String]("condition").exists(_.isEmpty) ⇒ >>("condition", None)
    }

    list("rewrites")

    super.expand
  }

  protected def condition(implicit source: YamlSourceReader): Option[Condition] = {
    <<?[Any]("condition") map WeakConditionReader.readReferenceOrAnonymous
  }

  protected def rewrites(implicit source: YamlSourceReader): List[Rewrite] = <<?[YamlList]("rewrites") match {
    case None                 ⇒ List.empty[Rewrite]
    case Some(list: YamlList) ⇒ list.map(RewriteReader.readReferenceOrAnonymous)
  }

  protected def balance(implicit source: YamlSourceReader) = <<?[String]("balance") match {
    case Some(value) if value == DefaultRoute.defaultBalance ⇒ None
    case other ⇒ other
  }

  override def validateName(name: String): String = if (GatewayPath.external(name)) name else super.validateName(name)
}

trait AbstractConditionReader extends YamlReader[Condition] {
  protected def createDefault(implicit source: YamlSourceReader): Condition = DefaultCondition(name, <<![String]("condition"))
}

object WeakConditionReader extends AbstractConditionReader with WeakReferenceYamlReader[Condition] {
  override protected def createReference(implicit source: YamlSourceReader): Condition = ConditionReference(reference)
}

object ConditionReader extends AbstractConditionReader {
  override protected def parse(implicit source: YamlSourceReader): Condition = createDefault
}

object RewriteReader extends YamlReader[Rewrite] with WeakReferenceYamlReader[Rewrite] {

  override protected def createReference(implicit source: YamlSourceReader): Rewrite = RewriteReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Rewrite = <<![String]("path") match {
    case definition ⇒
      PathRewrite.parse(name, definition) match {
        case Some(rewrite: PathRewrite) ⇒ rewrite
        case _                          ⇒ throwException(UnsupportedPathRewriteError(definition))
      }
  }
}

trait GatewayMappingReader[T <: Artifact] extends YamlReader[List[T]] {

  import YamlSourceReader._

  def mapping(entry: String)(implicit source: YamlSourceReader): List[T] = <<?[YamlSourceReader](entry).map(read(_)).getOrElse(Nil)

  protected def parse(implicit source: YamlSourceReader): List[T] = source.pull().keySet.map { key ⇒
    val yaml = <<![YamlSourceReader](key :: Nil)

    <<?[Any](key :: "port") match {
      case Some(value) ⇒ if (!acceptPort && !ignoreError) throwException(UnexpectedElement(Map[String, Any](key → "port"), value.toString))
      case None        ⇒ >>("port", key)(yaml)
    }

    if (onlyAnonymous) {
      if (ignoreError) {
        >>("name", None)(yaml)
        >>(Lookup.entry, None)(yaml)
      }
      reader.readAnonymous(yaml) match {
        case artifact ⇒ update(key, artifact)
      }
    }
    else {
      yaml.find[String](Lookup.entry)
      reader.read(yaml) match {
        case artifact ⇒ update(key, artifact)
      }
    }

  } toList

  protected def reader: AnonymousYamlReader[T]

  protected def acceptPort: Boolean

  protected def update(key: String, artifact: T)(implicit source: YamlSourceReader): T = artifact

  protected def onlyAnonymous = true

  protected def ignoreError = false
}

object BlueprintGatewayReader extends GatewayMappingReader[Gateway] {

  protected val reader = GatewayReader

  override protected def expand(implicit source: YamlSourceReader) = {
    source.pull().keySet.map { port ⇒
      <<![Any](port :: Nil) match {
        case route: String ⇒ >>(port :: "routes", route)
        case _             ⇒
      }
    }
    super.expand
  }

  protected def acceptPort = true

  override protected def update(key: String, gateway: Gateway)(implicit source: YamlSourceReader): Gateway = {
    val name = {
      val number = Port(key).number
      if (number != 0) number.toString else key
    }
    gateway.copy(port = gateway.port.copy(name = name))
  }
}

class InternalGatewayReader(override val acceptPort: Boolean, override val onlyAnonymous: Boolean = true, override val ignoreError: Boolean = false) extends GatewayMappingReader[Gateway] {

  protected val reader = ClusterGatewayReader

  override protected def expand(implicit source: YamlSourceReader) = {
    if (source.pull({ entry ⇒ entry == "sticky" || entry == "routes" }).nonEmpty) >>(Gateway.anonymous, <<-())
    super.expand
  }

  override protected def update(key: String, gateway: Gateway)(implicit source: YamlSourceReader): Gateway = {
    gateway.copy(port = gateway.port.copy(name = key))
  }
}

trait GatewayRouteValidation {
  this: NotificationProvider ⇒

  protected def validateGatewayRouteWeights: Gateway ⇒ Gateway = { gateway ⇒

    val defaultRoutes = gateway.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute])

    val weight = defaultRoutes.flatMap(_.weight).map(_.value).sum

    if (defaultRoutes.size == gateway.routes.size && weight != 0 && weight != 100) throwException(GatewayRouteWeightError(gateway))

    gateway
  }

  protected def validateGatewayRouteConditionStrengths: Gateway ⇒ Gateway = { gateway ⇒

    val strength = gateway.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).flatMap(_.conditionStrength)

    if (strength.exists(_.value < 0) || strength.exists(_.value > 100)) throwException(GatewayRouteConditionStrengthError(gateway))

    gateway
  }
}