package io.vamp.cli.commands

import java.util.concurrent.TimeUnit

import io.vamp.cli.commandline.Parameters
import io.vamp.model.artifact._
import io.vamp.model.reader._

import scala.concurrent.duration._

/**
 * Generate command is moved to separate trait, just for the shear size of it.
 */

trait Generate extends Parameters with IoUtils {

  protected def doGenerateCommand(subCommand: Option[String])(implicit vampHost: String, options: OptionMap) = {
    val fileContent = readOptionalFileContent
    printArtifact(subCommand match {

      case Some("breed") ⇒
        readArtifactStartingPoint[Breed](fileContent, BreedReader, emptyBreed) match {
          case db: DefaultBreed ⇒
            Some(db
              .copy(
                name = replaceValueString(name, db.name),
                deployable = getOptionalParameter(deployable) match {
                  case Some(dep: String) ⇒ Deployable(dep)
                  case None              ⇒ db.deployable
                })
            )
          case other ⇒ Some(other)
        }

      case Some("blueprint") ⇒
        val myScale: Option[Scale] = getOptionalParameter(scale).flatMap(s ⇒ Some(ScaleReference(name = s)))

        val mySla: Option[Sla] = getOptionalParameter(sla).flatMap(s ⇒ Some(SlaReference(name = s, escalations = List.empty)))
        readArtifactStartingPoint[Blueprint](fileContent, BlueprintReader, emptyBlueprint) match {
          case bp: DefaultBlueprint ⇒
            val newCluster = getOptionalParameter(cluster) flatMap (clusterName ⇒
              getOptionalParameter(breed) flatMap (breedName ⇒
                Some(List(Cluster(name = clusterName, services = List(Service(breed = BreedReference(breedName), Nil, scale = myScale, Nil)), gateways = Nil, sla = mySla)))
              )
            )
            Some(bp.copy(
              name = replaceValueString(name, bp.name),
              clusters = bp.clusters ++ newCluster.getOrElse(List.empty))
            )
          case other ⇒ Some(other)
        }

      case Some("escalation-cpu") ⇒
        readArtifactStartingPoint[Escalation](fileContent, EscalationReader, emptyScaleCpuEscalation) match {
          case df: ScaleCpuEscalation ⇒
            Some(
              df.copy(
                name = replaceValueString(name, df.name),
                minimum = replaceValueDouble(minimum, df.minimum),
                maximum = replaceValueDouble(maximum, df.maximum),
                scaleBy = replaceValueDouble(scale_by, df.scaleBy),
                targetCluster = replaceValueOptionalString(target_cluster, df.targetCluster)
              )
            )
          case other ⇒ Some(other)
        }

      case Some("escalation-memory") ⇒
        readArtifactStartingPoint[Escalation](fileContent, EscalationReader, emptyScaleMemoryEscalation) match {
          case df: ScaleMemoryEscalation ⇒
            Some(
              df.copy(
                name = replaceValueString(name, df.name),
                minimum = replaceValueDouble(minimum, df.minimum),
                maximum = replaceValueDouble(maximum, df.maximum),
                scaleBy = replaceValueDouble(scale_by, df.scaleBy),
                targetCluster = replaceValueOptionalString(target_cluster, df.targetCluster)
              )
            )
          case other ⇒ Some(other)
        }

      case Some("escalation_instance") ⇒
        readArtifactStartingPoint[Escalation](fileContent, EscalationReader, emptyScaleInstanceEscalation) match {
          case df: ScaleInstancesEscalation ⇒
            Some(
              df.copy(
                name = replaceValueString(name, df.name),
                minimum = replaceValueInt(minimum, df.minimum),
                maximum = replaceValueInt(maximum, df.maximum),
                scaleBy = replaceValueInt(scale_by, df.scaleBy),
                targetCluster = replaceValueOptionalString(target_cluster, df.targetCluster)
              )
            )
          case other ⇒ Some(other)
        }

      case Some("filter") ⇒
        readArtifactStartingPoint[Condition](fileContent, ConditionReader, emptyCondition) match {
          case df: DefaultCondition ⇒ Some(df.copy(name = replaceValueString(name, df.name)))
          case other                ⇒ Some(other)
        }

      case Some("rewrite") ⇒
        readArtifactStartingPoint[Rewrite](fileContent, RewriteReader, emptyRewrite) match {
          case df: DefaultCondition ⇒ Some(df.copy(name = replaceValueString(name, df.name)))
          case other                ⇒ Some(other)
        }

      case Some("routes") ⇒
        readArtifactStartingPoint[Route](fileContent, RouteReader, emptyRouting) match {
          case dr: DefaultRoute ⇒ Some(dr.copy(name = replaceValueString(name, dr.name)))
          case other            ⇒ Some(other)
        }

      case Some("scale") ⇒
        readArtifactStartingPoint[Scale](fileContent, ScaleReader, emptyScale) match {
          case ds: DefaultScale ⇒ Some(ds.copy(name = replaceValueString(name, ds.name)))
          case other            ⇒ Some(other)
        }

      case Some("sla-response-time-sliding-window") ⇒
        readArtifactStartingPoint[Sla](fileContent, SlaReader, emptyResponseTimeSlidingWindowSla) match {
          case sla: ResponseTimeSlidingWindowSla ⇒ Some(
            sla.copy(
              name = replaceValueString(name, sla.name),
              lower = replaceValueFiniteDuration(lower, sla.lower, TimeUnit.MILLISECONDS),
              upper = replaceValueFiniteDuration(upper, sla.upper, TimeUnit.MILLISECONDS),
              interval = replaceValueFiniteDuration(interval, sla.interval, TimeUnit.SECONDS),
              cooldown = replaceValueFiniteDuration(cooldown, sla.cooldown, TimeUnit.SECONDS)
            ))
        }

      case Some(invalid) ⇒ terminateWithError(s"Unsupported artifact '$invalid'")
      case None          ⇒ terminateWithError("Please specify an artifact type")
    }
    )
  }

  private def readArtifactStartingPoint[A](fileContent: Option[String], reader: YamlReader[A], alternative: A) = fileContent match {
    case Some(content: String) ⇒ reader.read(content)
    case None                  ⇒ alternative
  }

  private def replaceValueDouble(parameterName: Symbol, originalValue: Double)(implicit options: OptionMap): Double =
    getOptionalParameter(parameterName) match {
      case Some(v) ⇒ try {
        v.toDouble
      } catch {
        case e: Exception ⇒ terminateWithError(s"Invalid value $v for ${parameterName.name}", 0L)
      }
      case None ⇒ originalValue
    }

  private def replaceValueFiniteDuration(parameterName: Symbol, originalValue: FiniteDuration, unit: TimeUnit)(implicit options: OptionMap): FiniteDuration =
    getOptionalParameter(parameterName) match {
      case Some(v) ⇒ try {
        FiniteDuration(length = v.toInt, unit = unit)
      } catch {
        case e: Exception ⇒ terminateWithError(s"Invalid value $v for ${parameterName.name}", FiniteDuration(0, "ms"))
      }
      case None ⇒ originalValue
    }

  private def replaceValueInt(parameterName: Symbol, originalValue: Int)(implicit options: OptionMap): Int =
    getOptionalParameter(parameterName) match {
      case Some(v) ⇒ try {
        v.toInt
      } catch {
        case e: Exception ⇒ terminateWithError(s"Invalid value $v for ${parameterName.name}", 0)
      }
      case None ⇒ originalValue
    }

  private def replaceValueOptionalString(parameterName: Symbol, originalValue: Option[String])(implicit options: OptionMap): Option[String] =
    getOptionalParameter(parameterName) match {
      case Some(v) ⇒ Some(v)
      case None    ⇒ originalValue
    }

  private def replaceValueString(parameterName: Symbol, originalValue: String)(implicit options: OptionMap): String =
    getOptionalParameter(parameterName) match {
      case Some(v) ⇒ v
      case None    ⇒ originalValue
    }

  private def emptyBreed = DefaultBreed(name = "", deployable = Deployable(""), ports = List.empty, environmentVariables = List.empty, constants = List.empty, arguments = List.empty, dependencies = Map.empty)

  private def emptyBlueprint = DefaultBlueprint(name = "", clusters = List.empty, gateways = List.empty, environmentVariables = List.empty)

  private def emptyScale = DefaultScale(name = "", cpu = Quantity(0.0), memory = MegaByte(0.0), instances = 0)

  private def emptyRouting = DefaultRoute(name = "", path = "", weight = None, conditionStrength = None, condition = None, rewrites = List.empty, balance = None)

  private def emptyCondition = DefaultCondition(name = "", definition = "")

  private def emptyRewrite = PathRewrite(name = "", path = "", condition = "")

  private def emptyScaleCpuEscalation = ScaleCpuEscalation(name = "", minimum = 0, maximum = 0, scaleBy = 0, targetCluster = None)

  private def emptyScaleMemoryEscalation = ScaleMemoryEscalation(name = "", minimum = 0, maximum = 0, scaleBy = 0, targetCluster = None)

  private def emptyScaleInstanceEscalation = ScaleInstancesEscalation(name = "", minimum = 0, maximum = 0, scaleBy = 0, targetCluster = None)

  private def emptyResponseTimeSlidingWindowSla = ResponseTimeSlidingWindowSla(
    name = "",
    upper = FiniteDuration(length = 0L, unit = TimeUnit.MILLISECONDS),
    lower = FiniteDuration(length = 0L, unit = TimeUnit.MILLISECONDS),
    interval = FiniteDuration(length = 0L, unit = TimeUnit.SECONDS),
    cooldown = FiniteDuration(length = 0L, unit = TimeUnit.SECONDS),
    escalations = List.empty
  )

}
