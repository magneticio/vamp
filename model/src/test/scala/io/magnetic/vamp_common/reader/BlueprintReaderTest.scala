package io.magnetic.vamp_common.reader

import io.magnetic.vamp_core.reader.BlueprintReader
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BlueprintReaderTest extends FlatSpec with Matchers with ReaderTest {

  "BlueprintReader" should "read the simplest YAML (name and single breed only)" in {
    BlueprintReader.read(res("blueprint1.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List()),
      //'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), None, None, Map())), None))),
      'endpoints(Map()),
      'parameters(Map())
    )
  }
}
