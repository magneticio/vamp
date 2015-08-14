package io.vamp.core.container_driver.docker.wrapper

trait Dialect {

  def withDialect(source: Map[String, _], dialect: Map[Any, Any]): Map[String, _] = source.map {
    case (key, "") => key -> dialect.getOrElse(key, "")
    case (key, list: List[_]) => key -> (dialect.get(key) match {
      case Some(l: List[_]) => list ++ l
      case _ => list
    })
    case (key, map: collection.Map[_, _]) => key -> (dialect.get(key) match {
      case Some(m: collection.Map[_, _]) => m ++ map
      case _ => map
    })
    case (key, None) => key -> dialect.getOrElse(key, None)
    case (key, value) => key -> value
  }
}
