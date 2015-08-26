package io.vamp.core.container_driver.docker.wrapper.methods

import io.vamp.core.container_driver.docker.wrapper.Pull.Output
import io.vamp.core.container_driver.docker.wrapper.model.AuthConfig
import io.vamp.core.container_driver.docker.wrapper.{ Dialect, Docker, Requests, model }

trait Images extends Util {
  self: Requests ⇒

  object images {
    private[this] def base = host / "images"

    case class Images() extends Docker.Completion[List[model.Image]] {

      def apply[T](handler: Docker.Handler[T]) = request(base / "json")(handler)

    }

    def list = Images()

    case class Pull(
      private val _fromImage: String,
      private val _dialect: Map[Any, Any],
      private val _fromSrc: Option[String] = None,
      private val _repo: Option[String] = None,
      private val _tag: Option[String] = None,
      private val _registry: Option[String] = None)
        extends Docker.Stream[Output] with Dialect {

      override protected def streamer = Docker.Stream.chunk[Output]

      def fromImage(img: String) = copy(_fromImage = img)

      def fromSrc(src: String) = copy(_fromSrc = Some(src))

      def repo(r: String) = copy(_repo = Some(r))

      def tag(t: String) = copy(_tag = Some(t))

      def registry(r: String) = copy(_registry = Some(r))

      def dialect(d: Map[Any, Any]) = copy(_dialect = d)

      def apply[T](handler: Docker.Handler[T]) =
        request(base.POST / "create" <:< auth <<? query)(handler)

      private def auth: Traversable[(String, String)] = {
        def header(config: AuthConfig): Traversable[(String, String)] = {
          val source = withDialect(config.parameters, _dialect)
          if (source.exists({ case (key, value) ⇒ value.isInstanceOf[String] && value.asInstanceOf[String].nonEmpty }))
            Map("X-Registry-Auth" -> config.json(source))
          else None
        }

        authConfig match {
          case Some(config) ⇒ header(config)
          case None         ⇒ header(AuthConfig("", "", "", ""))
        }
      }

      private def query: Map[String, String] = withDialect(
        Map("fromImage" -> _fromImage)
          ++ _fromSrc.map("fromSrc" -> _)
          ++ _repo.map("repo" -> _)
          ++ _tag.map("tag" -> _)
          ++ _registry.map("registry" -> _), _dialect).map {
          case (key, value) ⇒ key -> value.toString
        }
    }

    def pull(image: String, dialect: Map[Any, Any]) = Pull(image, dialect)
  }

}
