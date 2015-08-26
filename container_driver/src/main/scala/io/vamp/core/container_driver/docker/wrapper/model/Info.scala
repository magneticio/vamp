package io.vamp.core.container_driver.docker.wrapper.model

/**
 * Not fully mapped, only fields we might need for debugging purposes
 *
 * Based on: https://docs.docker.com/reference/api/docker_remote_api_v1.19/#display-system-wide-information
 */

case class Info(
  containers: Int = 0,
  dockerRootDir: String = "",
  //driver: String = "",
  //driverStatus
  //executionDriver: String = "",
  //experimentalBuild: Boolean = false,
  //httpProxy: String = "",
  //httpsProxy: String = "",
  id: String = "",
  //ipv4Forwarding: Boolean = true,
  images: Int = 0,
  indexServerAddress: String = "",
  initPath: String = "",
  kernelVersion: String = "",
  //memTotal: Int = 0,
  //memoryLimit: Boolean = true,
  name: String = "",
  operatingSystem: String = "",
  //registryConfig
  systemTime: String = "")

