package io.vamp.container_driver.kubernetes

import scala.util.parsing.json.JSON

class CC[T] { def unapply(a:Any):Option[T] = Some(a.asInstanceOf[T]) }
object M extends CC[Map[String, Any]]
object S extends CC[String]

object KubernetesPatchHelper {

  def findName(request: String) : String = {
    val result = for {
      Some(M(map)) <- List(JSON.parseFull(request))
      M(metadata) = map("metadata")
      S(name) = metadata("name")
    } yield {
      name
    }
    result.head
  }

}
