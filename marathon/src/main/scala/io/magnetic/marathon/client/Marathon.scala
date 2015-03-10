package io.magnetic.marathon.client

import io.magnetic.marathon.client.api._
import io.magnetic.vamp_common.http.RestClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Marathon(url: String) {

  def info: Future[Info] = RestClient.request[Info](s"GET $url/v2/info")

  def apps: Future[Apps] = RestClient.request[Apps](s"GET $url/v2/apps?embed=apps.tasks")

  def createApp(app: CreateApp): Future[Any] = RestClient.request[Any](s"POST $url/v2/apps", app)

  def deleteApp(id: String): Future[Any] = RestClient.request[Any](s"DELETE $url/v2/apps/$id")
}
