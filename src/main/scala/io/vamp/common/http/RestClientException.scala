package io.vamp.common.http


case class RestClientException(statusCode: Int, message: String) extends RuntimeException(message){}
