package io.vamp.common.http

trait RequestEnvelope[T] {
  def request: T
}

trait ResponseEnvelope[T] {
  def response: T
}

object OffsetEnvelope {

  def normalize(page: Int, perPage: Int, maxPerPage: Int): (Int, Int) =
    (if (page < 1) 1 else page, if (perPage < 1) 1 else if (perPage > maxPerPage) maxPerPage else perPage)

  def normalize(total: Long, page: Int, perPage: Int, maxPerPage: Int): (Int, Int) = {
    val (p, pp) = normalize(page, perPage, maxPerPage)
    if (total < (p - 1) * pp) ((total / pp + 1).toInt, pp) else (p, pp)
  }
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