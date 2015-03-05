package io.magnetic.marathon.client

import dispatch.Http
import io.magnetic.marathon.client.api._
import io.magnetic.vamp_common.http.RestClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Marathon(url: String) {

  def info: Future[Info] = RestClient.request[Info](s"GET $url/v2/info")

  def apps: Future[Apps] = RestClient.request[Apps](s"GET $url/v2/apps")

  def createApp(app: App): Future[App] = RestClient.request[App](s"POST $url/v2/apps", Some(app))

  def app(id: String): Future[App] = RestClient.request[App](s"GET $url/v2/apps/$id", None, "app")


  //@RequestLine("POST /v2/apps") def createApp(app: App)

  //@RequestLine("PUT /v2/apps/{app_id}") def updateApp(@Named("app_id") appId: String, app: App)

  //@RequestLine("DELETE /v2/apps/{id}") def deleteApp(@Named("id") id: String)
}

object Marathon {
  def main(arguments: Array[String]): Unit = {

    val marathon = new Marathon("http://10.113.87.69:8080")

    val requests = for {
      info <- marathon.info
      apps <- marathon.apps
     // app <- marathon.app("/2/mysql/mysql")
    } yield (info, apps)

    requests onComplete {
      case Success(result) =>
        result.productIterator.foreach(el => println(el.toString))
        Http.shutdown()

      case Failure(error) =>
        println(error)
        Http.shutdown()
    }
  }
}
