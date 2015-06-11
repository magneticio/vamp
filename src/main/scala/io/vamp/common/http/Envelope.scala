package io.vamp.common.http

trait RequestEnvelope[T] {
  def request: T
}

trait ResponseEnvelope[T] {
  def response: T
}


trait OffsetRequestEnvelope[T] extends RequestEnvelope[T] {

  def page: Int

  def perPage: Int
}

trait OffsetResponseEnvelope[T] extends ResponseEnvelope[List[T]] {

  def page: Int

  def perPage: Int

  def total: Long
}