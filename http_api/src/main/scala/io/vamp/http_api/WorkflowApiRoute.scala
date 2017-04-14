package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes._
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.WorkflowApiController
import io.vamp.persistence.ArtifactExpansionSupport

trait WorkflowApiRoute extends AbstractRoute with WorkflowApiController {
  this: ArtifactExpansionSupport with HttpApiDirectives ⇒

  def workflowStatusRoute(implicit namespace: Namespace, timeout: Timeout) = path("workflows" / Segment / "status") { (workflow: String) ⇒
    get {
      onSuccess(workflowStatus(workflow)) { result ⇒
        respondWith(OK, result)
      }
    } ~ put {
      entity(as[String]) { request ⇒
        validateOnly { validateOnly ⇒
          onSuccess(workflowStatusUpdate(workflow, request, validateOnly)) { result ⇒
            respondWith(Accepted, result)
          }
        }
      }
    }
  }
}
