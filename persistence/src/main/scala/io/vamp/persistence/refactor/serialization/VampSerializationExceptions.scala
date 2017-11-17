package io.vamp.persistence.refactor.serialization

/**
  * Created by mihai on 11/16/17.
  */
case class ObjectFormatException(objectAsString: String, `type`: String) extends Exception(s"JsonFormatException: Cannot Interpret ${objectAsString} as instance of ${`type`}")