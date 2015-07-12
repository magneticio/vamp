package io.vamp.core.container_driver.docker.wrapper.methods

import io.vamp.core.container_driver.docker.wrapper.Pull.Output
import io.vamp.core.container_driver.docker.wrapper.{Docker, Requests, model}

trait Images extends Util {
  self: Requests =>

  object images {
    private[this] def base = host / "images"

    case class Images() extends Docker.Completion[List[model.Image]] {

      def apply[T](handler: Docker.Handler[T]) = request(base / "json")(handler)

    }

    def list = Images()

    case class Pull(
                     private val _fromImage: String,
                     private val _fromSrc: Option[String] = None,
                     private val _repo: Option[String] = None,
                     private val _tag: Option[String] = None,
                     private val _registry: Option[String] = None)
      extends Docker.Stream[Output] {

      override protected def streamer = Docker.Stream.chunk[Output]

      def fromImage(img: String) = copy(_fromImage = img)

      def fromSrc(src: String) = copy(_fromSrc = Some(src))

      def repo(r: String) = copy(_repo = Some(r))

      def tag(t: String) = copy(_tag = Some(t))

      def registry(r: String) = copy(_registry = Some(r))

      def apply[T](handler: Docker.Handler[T]) =
        request(base.POST / "create" <:< authConfig.map("X-Registry-Auth" -> _.json) <<? query)(handler)

      def query = (
        Map("fromImage" -> _fromImage)
          ++ _fromSrc.map("fromSrc" -> _)
          ++ _repo.map("repo" -> _)
          ++ _tag.map("tag" -> _)
          ++ _registry.map("registry" -> _)
        )
    }

    def pull(image: String) = Pull(image)

  }


}
