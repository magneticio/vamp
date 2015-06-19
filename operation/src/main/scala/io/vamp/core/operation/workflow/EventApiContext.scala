package io.vamp.core.operation.workflow

import io.vamp.common.akka.FutureSupport

import scala.concurrent.ExecutionContext

class EventApiContext(ec: ExecutionContext) extends FutureSupport {

  implicit val executionContext = ec

  def tag(t: String) = {}

  def `type`(t: String) = {}

  def value(v: AnyRef) = {}

  def publish() = {}

  def lt(time: String) = {}

  def lte(time: String) = {}

  def gt(time: String) = {}

  def gte(time: String) = {}

  def count() = {}

  def max() = {}

  def min() = {}

  def average() = {}

  def reset() = {}
}
