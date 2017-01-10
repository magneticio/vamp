package io.vamp.common.http

import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }

/**
 * http://stackoverflow.com/questions/34296506/close-akka-http-websocket-connection-from-server/40962834#40962834
 */
class TerminateFlowStage[T](pred: T ⇒ Boolean, forwardTerminatingMessage: Boolean = false, terminate: Boolean = true) extends GraphStage[FlowShape[T, T]] {

  val in = Inlet[T]("TerminateFlowStage.in")
  val out = Outlet[T]("TerminateFlowStage.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      setHandlers(in, out, new InHandler with OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }

        override def onPush(): Unit = {
          val chunk = grab(in)

          if (pred(chunk)) {
            if (forwardTerminatingMessage)
              push(out, chunk)
            if (terminate)
              failStage(new RuntimeException("Flow terminated by TerminateFlowStage"))
            else
              completeStage()
          }
          else push(out, chunk)
        }
      })
    }
}
