package io.vamp.container_driver.docker.wrapper.model

case class Image(id: String, repoTags: List[String] = Nil)
