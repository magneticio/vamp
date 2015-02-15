package io.magnetic.vamp_core.reader

import io.magnetic.vamp_core.model._

import scala.language.postfixOps

object BlueprintReader extends YamlReader[Blueprint] {

  override def expand(implicit source: YamlObject) = source

  override def parse(implicit source: YamlObject): Blueprint = {

    val clusters = <<?[YamlObject]("clusters") match {
      case None => List[Cluster]()
      case Some(map) => map.map({
        case (name: String, cluster: collection.Map[_, _]) =>
          implicit val source = cluster.asInstanceOf[YamlObject]

          val sla = <<?[Any]("sla").flatMap {
            s => Some(SlaReader.readReference(s))
          }

          <<?[List[YamlObject]]("services") match {
            case None => Cluster(name, List(), sla)
            case Some(list) => Cluster(name, list.map(service(_)), sla)
          }
      }).toList
    }

    Blueprint(name, clusters, map("endpoints"), map("parameters"))
  }

  override protected def validate(blueprint: Blueprint): Blueprint = blueprint // validate endpoints, parameters (cluster references)

  private def service(implicit source: YamlObject): Service = {
    val breed = BreedReader.readReference(<<![YamlObject]("breed"))
    val scale = None
    val routing = None

    Service(breed, scale, routing)
  }

  private def map(path: YamlPath)(implicit source: YamlObject): Map[String, String] = <<?[YamlObject](path) match {
    case None => Map()
    case Some(map) => map.map {
      case (name: String, _) =>
        implicit val source = map.asInstanceOf[YamlObject]
        (name, <<![String](name))
    } toMap
  }
}

object SlaReader extends YamlReader[Sla] with WeakReferenceYamlReader[Sla] {

  override protected def validate(implicit source: YamlObject): YamlObject = {
    if (source.filterKeys(k => k != "name" && k != "escalations").nonEmpty) super.validate
    source
  }

  override protected def createReference(implicit source: YamlObject): Sla = SlaReference(reference.get, escalations)

  override protected def createAnonymous(implicit source: YamlObject): Sla = AnonymousSla(`type`, escalations, parameters)
  
  protected def escalations(implicit source: YamlObject): List[Escalation] = <<?[YamlList]("escalations") match {
    case None => List[Escalation]()
    case Some(list: YamlList) => list.map {
      EscalationReader.readReference
    }
  }

  override protected def parameters(implicit source: YamlObject): Map[String, Any] = super.parameters.filterKeys(_ != "escalations")
}

object EscalationReader extends YamlReader[Escalation] with WeakReferenceYamlReader[Escalation] {

  override protected def createReference(implicit source: YamlObject): Escalation = EscalationReference(reference.get)

  override protected def createAnonymous(implicit source: YamlObject): Escalation = AnonymousEscalation(`type`, parameters)
}
