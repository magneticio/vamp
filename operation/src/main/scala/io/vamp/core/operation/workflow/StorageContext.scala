package io.vamp.core.operation.workflow

import scala.collection.mutable

class StorageContext(storage: Map[String, Any]) {

  private val store = mutable.Map[String, Any]() ++ storage

  def all() = store.toMap

  def get(key: String) = store.get(key).orNull

  def getOrElse(key: String, default: Any = null) = store.getOrElse(key, default)

  def remove(key: String) = store.remove(key).orNull

  def put(key: String, value: Any) = store.put(key, value).orNull

  def clear() = store.clear()
}
