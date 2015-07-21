package io.vamp.core.cli.commands

import java.io.File

import io.vamp.core.cli.backend.VampHostCalls
import io.vamp.core.cli.commandline.Parameters
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.model.serialization.CoreSerializationFormat
import org.json4s.native.Serialization._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

import scala.io.Source

/**
 * Methods for reading from files / stdin & writing to  stdout
 */

trait IoUtils extends Parameters {

  implicit val formats = CoreSerializationFormat.default

  protected def readOptionalFileContent(implicit options: OptionMap): Option[String] = getOptionalParameter('file) match {
    case Some(fileName) => if (java.nio.file.Files.exists(new File(fileName).toPath)) {
      Some(Source.fromFile(fileName).getLines().mkString("\n"))
    } else {
      terminateWithError(s"File '$fileName' not found")
    }
    case None => getOptionalParameter('stdin) match {
      case Some(value) => Some(Source.stdin.getLines().mkString("\n"))
      case None => None
    }
  }

  protected def readFileContent(implicit options: OptionMap): String = readOptionalFileContent match {
    case Some(content) => content
    case None => terminateWithError("No file specified", "")
  }

  protected def printArtifact(artifact: Option[Artifact])(implicit options: OptionMap) = {
    getOptionalParameter(json) match {
      case None => artifact.foreach(a => println(artifactToYaml(a)))
      case _ => println(VampHostCalls.prettyJson(artifact))
    }
  }

  protected def artifactToYaml(artifact: Artifact): String = {
    def toJson(any: Any) = {
      any match {
        case value: AnyRef => write(value)
        case value => write(value.toString)
      }
    }
    new Yaml().dumpAs(new Yaml().load(toJson(artifact)), Tag.MAP, FlowStyle.BLOCK)
  }

}
