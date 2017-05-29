package io.vamp.lifter.artifact

import java.nio.file.{Path, Paths}

import akka.pattern.ask
import cats.data.{EitherT, Kleisli, NonEmptyList}
import cats.free.Free
import cats.~>
import io.vamp.config.Config
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.lifter.ArtifactLifterSeed
import io.vamp.lifter.notification._
import io.vamp.lifter.persistence.SqlInterpreter.LifterResult
import io.vamp.model.artifact.{DefaultBreed, Deployable}
import io.vamp.model.reader.WorkflowReader
import io.vamp.persistence.PersistenceActor
import cats.implicits.{catsStdInstancesForEither, catsStdInstancesForList}
import cats.instances.future.catsStdInstancesForFuture
import cats.implicits.toTraverseOps
import io.vamp.operation.controller

import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Left, Right, Success, Try}

/**
 * Initializes supplied Artifacts in the configuration, does not stop on failure.
 */
class ArtifactInitializationActor extends CommonSupportForActors with LifterNotificationProvider with ArtifactLiftAction {

  def receive = {
    case "init" ⇒

      Config.read[ArtifactLifterSeed](configLocation) match {
          case Left(errorMessages) ⇒
            errorMessages.toList.foreach(log.error)
            log.error("Unable to perform initialization of Artifacts due to missing configuration values.")
          case Right(artifactLifterSeed) ⇒
            val artifactInitActions: ArtifactLiftAction[Unit] = for {
              _ ← loadFiles
              _ ← loadResources
            } yield ()

            val executeInitActions: ArtifactResult[Unit] =
              artifactInitActions.foldMap(artifactLiftInterpreter)

            executeInitActions.run(artifactLifterSeed).value.foreach {
              case Left(errorMessage) ⇒
                reportException(PersistenceInitializationFailure(errorMessage))
              case Right(_) ⇒
                info(ArtifactInitializationSuccess)
            }
        }
  }

  val configLocation: String = "vamp.lifter.artifact"

  override def preStart(): Unit = {
    Config.read[ArtifactLifterSeed]("vamp.lifter.artifact") match {
      case Right(artifactLifterSeed) ⇒
        context.system.scheduler.scheduleOnce(artifactLifterSeed.postpone, self, "init")
      case _ ⇒ ()
    }

  }

}

/**
 * Provides a Safe way of initializing Artifacts
 */
trait ArtifactLiftAction extends controller.ArtifactApiController with controller.DeploymentApiController {
  this: CommonActorLogging with CommonProvider ⇒

  implicit lazy val timeout = PersistenceActor.timeout()

  type ArtifactLiftAction[A] = Free[ArtifactLiftDSL, A]

  type ArtifactResult[A] = Kleisli[LifterResult, ArtifactLifterSeed, A]

  sealed trait ArtifactLiftDSL[A]
  case object LoadFiles extends ArtifactLiftDSL[Unit]
  case object LoadResources extends ArtifactLiftDSL[Unit]

  def loadFiles: ArtifactLiftAction[Unit] =
    Free.liftF(LoadFiles)

  def loadResources: ArtifactLiftAction[Unit] =
    Free.liftF(LoadResources)

  val artifactLiftInterpreter: ArtifactLiftDSL ~> ArtifactResult = new (ArtifactLiftDSL ~> ArtifactResult) {
    override def apply[A](artifactLiftDSL: ArtifactLiftDSL[A]): ArtifactResult[A] =
      Kleisli[LifterResult, ArtifactLifterSeed, A] { als ⇒
        artifactLiftDSL match {
          case LoadFiles ⇒
            EitherT(Future.successful(als.files.traverse[({ type F[B] = Either[String, B] })#F, Unit] { file ⇒
              val result = for {
                path ← Try(Paths.get(file))
                source ← Try(Source.fromFile(file).mkString)
              } yield load(path, source, als.force)

              result match {
                case Failure(exception) ⇒ Left(s"Unable to load file: '$file'.")
                case Success(value)     ⇒ Right(value)
              }
            }.map(_ ⇒ ())))
          case LoadResources ⇒
            EitherT(Future.successful(als.resources.traverse[({ type F[B] = Either[String, B] })#F, Unit] { resource ⇒
              val result = for {
                path ← Try(Paths.get(resource))
                _ = log.info(s"Retrieving resource path $path.")
                source ← Try(Source.fromInputStream(getClass.getResourceAsStream(path.toString)))
                _ = log.info(s"Retrieving source: ${source.mkString}")
              } yield load(path, source.mkString, als.force)

              result match {
                case Failure(exception) ⇒ Left(s"Unable to load resource: '$resource'.")
                case Success(value)     ⇒ Right(value)
              }
            }.map(_ ⇒ ())))
        }
      }
  }

  protected def loadResources(force: Boolean = false): List[String] ⇒ Unit = {
    _.map(Paths.get(_)).foreach(path ⇒ load(path, Source.fromInputStream(getClass.getResourceAsStream(path.toString)).mkString, force))
  }

  private def load(path: Path, source: String, force: Boolean): Unit = {

    val `type` = path.getParent.getFileName.toString
    val fileName = path.getFileName.toString
    val name = fileName.substring(0, fileName.lastIndexOf("."))

    exists(`type`, name).map {
      case true ⇒
        if (force) {
          log.info(s"Updating artifact: ${`type`}/$name")
          create(`type`, fileName, name, source)
        } else
          log.info(s"Ignoring creation of artifact because it exists: ${`type`}/$name")

      case false ⇒
        log.info(s"Creating artifact: ${`type`}/$name")
        create(`type`, fileName, name, source)
    }
  }

  private def exists(`type`: String, name: String): Future[Boolean] = {
    readArtifact(`type`, name, expandReferences = false, onlyReferences = false).map {
      case Some(_) ⇒ true
      case _       ⇒ false
    }
  }

  private def create(`type`: String, fileName: String, name: String, source: String): Future[Any] = {
    if (`type` == "breeds" && fileName.endsWith(".js"))
      actorFor[PersistenceActor] ? PersistenceActor.Update(DefaultBreed(name, Map(), Deployable("application/javascript", source), Nil, Nil, Nil, Nil, Map(), None), Some(source))
    else if (`type` == "workflows")
      actorFor[PersistenceActor] ? PersistenceActor.Update(WorkflowReader.read(source), Some(source))
    else
      updateArtifact(`type`, name, source, validateOnly = false)
  }

}
