package io.magnetic.vamp_common.pulse

/**
 * Created by lazycoder on 12/03/15.
 */
trait PulseClientProvider {
  protected val url: String
  protected val client = new PulseClient(url)
}
