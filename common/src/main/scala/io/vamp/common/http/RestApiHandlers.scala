package io.vamp.common.http

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.Logger
import io.vamp.common.notification.NotificationErrorException
import org.slf4j.LoggerFactory

trait RestApiHandlers {
  this: RestApiDirectives ⇒

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  def exceptionHandler = ExceptionHandler {

    case e: NotificationErrorException ⇒
      respondWithError(BadRequest, s"${e.message}")

    case e: Exception ⇒
      extractUri { uri ⇒
        logger.error("Request to {} could not be handled normally: {}", uri, e.getMessage)
        respondWithError(InternalServerError)
      }
  }

  def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case MalformedRequestContentRejection(message, e: NotificationErrorException) ⇒
        respondWithError(BadRequest, s"$message")
    }
    .handle {
      case MalformedRequestContentRejection(message, ex) ⇒
        logger.error(ex.getMessage)
        respondWithError(BadRequest)
    }
    .handle {
      case MalformedRequestContentRejection(message, _) ⇒
        respondWithError(BadRequest)
    }
    .handle {
      case MalformedHeaderRejection(_, message, _) ⇒
        respondWithError(BadRequest, s"$message")
    }
    .result().withFallback(RejectionHandler.default)

  private def respondWithError(status: StatusCode, message: String = "") = {
    respondWith(
      status = status,
      response = "message" -> (if (status == InternalServerError) "Internal server error." else s"The request content was malformed. $message")
    )
  }
}
