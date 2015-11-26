package io.vamp.container_driver.docker.wrapper

trait Dialect {

  def withDialect(source: Map[String, _], dialect: Map[Any, Any]): Map[String, _] = source.map {
    case (key, "") ⇒ key -> dialect.getOrElse(key, "")
    case (key, list: List[Any]) ⇒ key -> (dialect.get(key) match {
      case Some(l: List[Any]) ⇒ list ++ l
      case _                  ⇒ list
    })
    case (key, map: Map[_, _]) ⇒ key -> (dialect.get(key) match {
      case Some(m: Map[_, _]) ⇒ m ++ map
      case _                             ⇒ map
    })
    case (key, None)  ⇒ key -> dialect.getOrElse(key, None)
    case (key, value) ⇒ key -> value
  }
}
