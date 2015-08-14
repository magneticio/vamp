package io.vamp.core.model.serialization

import io.vamp.core.model.artifact._
import io.vamp.core.model.resolver.TraitResolver
import org.json4s.FieldSerializer._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

object BreedSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new BreedSerializer() :+
    new DeployableSerializer()

  override def fieldSerializers = super.fieldSerializers :+
    new BreedFieldSerializer()
}

trait TraitDecomposer extends TraitResolver {

  def traits(traits: List[Trait], alias: Boolean = true) = {
    def traitName(name: String) = TraitReference.referenceFor(name) match {
      case Some(TraitReference(c, g, Host.host)) if g == TraitReference.groupFor(TraitReference.Hosts) => c
      case Some(TraitReference(c, g, n)) => s"$c${TraitReference.delimiter}$n"
      case None => name
    }

    new JObject(traits.map(t => t.name -> t).toMap.values.map { t =>
      val name = traitName(if (alias) asName(t.name, t.alias) else t.name)
      val value = if (t.value == null || t.value.isEmpty) JNull
      else {
        JString(t match {
          case EnvironmentVariable(_, _, v, i) => i.getOrElse(v.get)
          case any => t.value.get
        })
      }

      JField(name, value)
    } toList)
  }
}

class BreedSerializer extends ArtifactSerializer[Breed] with TraitDecomposer with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case breed: BreedReference =>serializeReference(breed)
    case breed: DefaultBreed =>
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(breed.name))
      list += JField("deployable", Extraction.decompose(breed.deployable))
      list += JField("ports", traits(breed.ports))
      list += JField("environment_variables", traits(breed.environmentVariables))
      list += JField("constants", traits(breed.constants))

      val dependencies = breed.dependencies.map {
        case (name, reference: Reference) => JField(name, JString(reference.name))
        case (name, dependency) => JField(name, Extraction.decompose(dependency))
      } toList

      list += JField("dependencies", new JObject(dependencies))

      new JObject(list.toList)
  }
}

class DeployableSerializer extends ArtifactSerializer[Deployable] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case d: Deployable => JString(d.name)
  }
}

class BreedFieldSerializer extends ArtifactFieldSerializer[DefaultBreed] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] =
    ignore("traits") orElse renameTo("environmentVariables", "environment_variables")
}

