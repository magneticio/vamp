package io.vamp.operation.workflow

import io.vamp.model.workflow.ScheduledWorkflow

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class StorageContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext {

  private val store = mutable.Map[String, Any]() ++ scheduledWorkflow.storage

  def all() = store.toMap

  def get(key: String) = store.get(key).orNull

  def getOrElse(key: String, default: Any = null) = store.getOrElse(key, default)

  def remove(key: String) = store.remove(key).orNull

  def put(key: String, value: Any) = store.put(key, value).orNull

  def clear() = store.clear()
}
