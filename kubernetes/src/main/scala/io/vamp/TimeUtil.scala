package io.vamp

import com.typesafe.scalalogging.LazyLogging

trait TimeUtil extends LazyLogging {
  def time[R](definition: String, block: ⇒ R): R = {
    val t0 = System.nanoTime()
    val result = block // call-by-name
    val t1 = System.nanoTime()
    logger.info(s"Processing of ${definition} took ${(t1 - t0) / 1000000} ms")
    result
  }
}
