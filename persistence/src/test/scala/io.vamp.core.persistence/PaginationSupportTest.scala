package io.vamp.core.persistence

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.http.OffsetResponseEnvelope
import org.junit.runner.RunWith
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.{ExecutionContext, Future}

@RunWith(classOf[JUnitRunner])
class PaginationSupportTest extends FlatSpec with Matchers with PaginationSupport with ScalaFutures with ExecutionContextProvider {

  case class ResponseEnvelope(response: List[Int], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Int]

  implicit def executionContext = ExecutionContext.global

  implicit val defaultPatience = PatienceConfig(timeout = Span(3, Seconds), interval = Span(100, Millis))

  "PaginationSupport" should "collect all from single page" in {
    val list = (1 to 5).toList

    val source = (page: Int, perPage: Int) => Future {
      ResponseEnvelope(list.take(perPage), list.size, page, perPage)
    }

    whenReady(allPages(source, 5))(_ shouldBe list)
  }

  it should "collect all from multiple pages" in {
    val list = (1 to 15).toList

    val source = (page: Int, perPage: Int) => Future {
      ResponseEnvelope(list.slice((page - 1) * perPage, page * perPage), list.size, page, perPage)
    }

    whenReady(allPages(source, 5))(_ shouldBe list)
  }

  it should "collect all from multiple pages without round total / per page" in {
    val list = (1 to 17).toList

    val source = (page: Int, perPage: Int) => Future {
      ResponseEnvelope(list.slice((page - 1) * perPage, page * perPage), list.size, page, perPage)
    }

    whenReady(allPages(source, 5))(_ shouldBe list)
  }
}
