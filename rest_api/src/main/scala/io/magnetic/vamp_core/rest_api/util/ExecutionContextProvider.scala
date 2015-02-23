package io.magnetic.vamp_core.rest_api.util

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait ExecutionContextProvider {
  implicit def executionContext: ExecutionContext
}
