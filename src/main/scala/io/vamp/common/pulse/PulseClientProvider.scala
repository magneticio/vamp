package io.vamp.common.pulse

/**
 * Created by lazycoder on 12/03/15.
 */
trait PulseClientProvider {
  protected val url: String

  lazy protected val client = {
    require(url != null && url.contains("http"))
    new PulseClient(url)
  }
}
