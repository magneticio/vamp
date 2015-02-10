package io.magnetic.marathon.client

import io.magnetic.marathon.client.api._
import io.magnetic.vamp_common.http.Client

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class Marathon(url: String) {

  def info: Future[Info] = Client.request[Info](s"$url/v2/info")

  def apps: Future[Apps] = Client.request[Apps](s"$url/v2/apps")

  //@RequestLine("GET /v2/apps/{id}/tasks") def getAppTasks(@Named("id") id: String): Tasks

  //@RequestLine("POST /v2/apps") def createApp(app: App)

  //@RequestLine("PUT /v2/apps/{app_id}") def updateApp(@Named("app_id") appId: String, app: App)

  //@RequestLine("DELETE /v2/apps/{id}") def deleteApp(@Named("id") id: String)
}

object Marathon {

  def main(arguments: Array[String]): Unit = {
    val marathon = new Marathon("http://10.20.79.175:8080")

    List(/*marathon.info, */marathon.apps).foreach(_ onComplete {
      case Success(result) => println(result)
      case Failure(error) => println(error)
    })
  }
}
