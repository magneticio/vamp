package io.vamp.container_driver.docker.wrapper.model

case class ContainerState(
  running: Boolean = false,
  paused: Boolean = false,
  restarting: Boolean = false,
  oomKilled: Boolean = false,
  dead: Boolean = false,
  pid: Int = 0,
  exitCode: Int = 0,
  error: String = "",
  startedAt: String = "",
  finishedAt: String = "")

