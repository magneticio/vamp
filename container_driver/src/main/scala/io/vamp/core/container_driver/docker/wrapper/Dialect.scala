package io.vamp.core.container_driver.docker.wrapper

trait Dialect {

  def withDialect(source: Map[String, _], dialect: Map[Any, Any]): Map[String, _] = source.map {
    case (key, "") => key -> dialect.getOrElse(key, "")
    case (key, l: List[_]) if l.isEmpty => key -> dialect.getOrElse(key, Nil)
    case (key, m: Map[_, _]) if m.isEmpty => key -> dialect.getOrElse(key, Map())
    case (key, None) => key -> dialect.getOrElse(key, None)
    case (key, value) => key -> value
  }
}
