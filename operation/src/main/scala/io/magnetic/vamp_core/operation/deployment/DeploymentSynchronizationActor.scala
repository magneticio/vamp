package io.magnetic.vamp_core.operation.deployment

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{Broadcast, FlowGraph, ForeachSink, Source}
import io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.{SynchronizeAll, Synchronize}

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.{Failure, Success}

object DeploymentSynchronizationActor extends ActorDescription {

  def props: Props = Props(new DeploymentSynchronizationActor)

  trait DeploymentSynchronizationMessages

  case class SynchronizeAll() extends DeploymentSynchronizationMessages
  
  case class Synchronize(deployment: Deployment) extends DeploymentSynchronizationMessages

}

class DeploymentSynchronizationActor extends Actor with ActorLogging with ActorSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  def receive: Receive = {
    case SynchronizeAll => println("Synchronize All")
    case Synchronize(deployment) =>
      //context.system.scheduler.schedule()
      println("Synchronize")
      run()
  }

  def run() = {
    //    implicit val system = ActorSystem("Sys")
    //    import system.dispatcher
    implicit val materializer = ActorFlowMaterializer()
    // generate random numbers
    val maxRandomNumberSize = 1000000
    val primeSource: Source[Int] =
      Source(() => Iterator.continually(ThreadLocalRandom.current().nextInt(maxRandomNumberSize))).
        // filter prime numbers
        filter(rnd => isPrime(rnd)).
        // and neighbor +2 is also prime
        filter(prime => isPrime(prime + 2))
    // write to file sink
    //val output = new PrintWriter(new FileOutputStream("target/primes.txt"), true)
    val slowSink = ForeachSink[Int] { prime =>
      println(prime)
      // simulate slow consumer
      Thread.sleep(1000)
    }
    // console output sink
    val consoleSink = ForeachSink[Int](println)
    // send primes to both slow file sink and console sink using graph API
    val materialized = FlowGraph { implicit builder =>
      import akka.stream.scaladsl.FlowGraphImplicits._
      val broadcast = Broadcast[Int] // the splitter - like a Unix tee
      primeSource ~> broadcast ~> slowSink // connect primes to splitter, and one side to file
      broadcast ~> consoleSink // connect other side of splitter to console
    }.run()
    // ensure the output file is closed and the system shutdown upon completion
    materialized.get(slowSink).onComplete {
      case Success(_) =>
        println("Done")
      //Try(output.close())
      //system.shutdown()
      case Failure(e) =>
        println(s"Failure: ${e.getMessage}")
      //Try(output.close())
      //system.shutdown()
    }
  }

  def isPrime(n: Int): Boolean = {
    if (n <= 1) false
    else if (n == 2) true
    else !(2 to (n - 1)).exists(x => n % x == 0)
  }
}
