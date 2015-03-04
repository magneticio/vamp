package io.magnetic.vamp_core.operation.deployment

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{FlowGraph, ForeachSink, Source}
import io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.Synchronize

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.{Failure, Success}

object DeploymentSynchronizationActor extends ActorDescription {

  def props: Props = Props(new DeploymentSynchronizationActor)

  case class Synchronize(deployment: Deployment)

}

class DeploymentSynchronizationActor extends Actor with ActorLogging with ActorSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  def receive: Receive = {
    case Synchronize(deployment) => //synchronize(deployment)
  }

  private def synchronize(deployment: Deployment) = {

    implicit val materializer = ActorFlowMaterializer()

    val primeSource: Source[Int] = Source(() => Iterator.continually(ThreadLocalRandom.current().nextInt(1000000))).
      filter(rnd => isPrime(rnd)).
      filter(prime => isPrime(prime + 2)).
      take(5)

    val console = ForeachSink[Int] { prime =>
      println(prime)
    }

    val materialized = FlowGraph { implicit builder =>
      import akka.stream.scaladsl.FlowGraphImplicits._
      primeSource.map({ prime =>
        Thread.sleep(100)
        prime
      }) ~> console
      
    }.run()

    materialized.get(console).onComplete {
      case Success(_) =>
        println("Done")
      case Failure(e) =>
        println(s"Failure: ${e.getMessage}")
    }
  }

  def isPrime(n: Int): Boolean = {
    if (n <= 1) false
    else if (n == 2) true
    else !(2 to (n - 1)).exists(x => n % x == 0)
  }
}
