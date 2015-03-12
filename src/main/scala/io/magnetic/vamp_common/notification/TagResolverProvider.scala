package io.magnetic.vamp_common.notification

/**
 * Created by lazycoder on 12/03/15.
 */
trait TagResolverProvider {
  def resolveTags(notification: Notification, predefinedTags: List[String] = List.empty): List[String]
}

trait DefaultTagResolverProvider extends TagResolverProvider {
  override def resolveTags(notification: Notification, predefinedTags: List[String]): List[String] = {
    predefinedTags
  }
}