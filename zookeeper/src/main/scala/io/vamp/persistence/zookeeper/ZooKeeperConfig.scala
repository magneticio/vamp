package io.vamp.persistence.zookeeper

import io.vamp.common.{ Config, Namespace }

object ZooKeeperConfig {
  import ZooKeeperStoreActor._

  def apply()(implicit namespace: Namespace): ZooKeeperConfig = ZooKeeperConfig(
    servers = Config.string(s"$config.servers")(),
    sessionTimeout = Config.int(s"$config.session-timeout")(),
    connectTimeout = Config.int(s"$config.connect-timeout")()
  )
}

case class ZooKeeperConfig(servers: String, sessionTimeout: Int, connectTimeout: Int)
