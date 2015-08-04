package io.vamp.core.model.artifact

import org.scalatest.prop.{ GeneratorDrivenPropertyChecks, PropertyChecks }
import org.scalatest.{ OptionValues, Matchers, WordSpec }

class TraitReferenceSpec extends WordSpec with Matchers with OptionValues with GeneratorDrivenPropertyChecks {

  "TraitReference" should {
    "convert string to TraitReference via: referenceFor" in {
      forAll("cluster", "group", "name") { (cluster: String, group: String, name: String) ⇒
        whenever(!cluster.contains('.') && !group.contains('.') && !name.contains('.')) {
          val traitReferenceString = s"$cluster.$group.$name"
          TraitReference.referenceFor(traitReferenceString).value should be(TraitReference(cluster, group, name))
        }
      }
    }

    "referenceFor & toReference: referenceFor(x.toReference) should yield same result" in {
      forAll("cluster", "group", "name") { (cluster: String, group: String, name: String) ⇒
        whenever(!cluster.contains('.') && !group.contains('.') && !name.contains('.')) {
          TraitReference.referenceFor(TraitReference(cluster, group, name).reference).value should be(TraitReference(cluster, group, name))
        }
      }
    }
  }
}
