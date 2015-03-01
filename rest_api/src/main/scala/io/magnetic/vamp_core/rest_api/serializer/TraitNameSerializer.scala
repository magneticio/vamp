package io.magnetic.vamp_core.rest_api.serializer

import io.magnetic.vamp_core.model.artifact.Trait
import org.json4s.CustomKeySerializer

class TraitNameSerializer extends CustomKeySerializer[Trait.Name](format => ( {
  case value => Trait.Name.asName(value)
}, {
  case name: Trait.Name => name.toString
}))