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
          val sla = SlaReader.readOptionalReference("sla")

          <<?[List[YamlObject]]("services") match {
            case None => Cluster(name, List(), sla)
            case Some(list) => Cluster(name, list.map(service(_)), sla)
          }
      }).toList
    }

    Blueprint(name, clusters, stringMap("endpoints"), stringMap("parameters"))
  }

  override protected def validate(blueprint: Blueprint): Blueprint = blueprint // validate endpoints, parameters (cluster references)

  private def service(implicit source: YamlObject): Service = {

    val routing = None

    Service(BreedReader.readReference(<<![YamlObject]("breed")), ScaleReader.readOptionalReference("scale"), routing)
  }
}

object SlaReader extends YamlReader[Sla] with WeakReferenceYamlReader[Sla] {

  override protected def validate(implicit source: YamlObject): YamlObject = {
    if (source.filterKeys(k => k != "name" && k != "escalations").nonEmpty) super.validate
    source
  }

  override protected def createReference(implicit source: YamlObject): Sla = SlaReference(reference, escalations)

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

  override protected def createReference(implicit source: YamlObject): Escalation = EscalationReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Escalation = AnonymousEscalation(`type`, parameters)
}

object ScaleReader extends YamlReader[Scale] with WeakReferenceYamlReader[Scale] {

  override protected def createReference(implicit source: YamlObject): Scale = ScaleReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Scale = AnonymousScale(<<![Double]("cpu"), <<![Double]("memory"), <<![Int]("instances"))
}
