package io.vamp.common.notification

/**
 * Created by lazycoder on 12/03/15.
 */
trait TagResolverProvider {
  def resolveTags(notification: Notification): List[String]
}

trait DefaultTagResolverProvider extends TagResolverProvider {
  override def resolveTags(notification: Notification): List[String] = {
    List.empty
  }
}