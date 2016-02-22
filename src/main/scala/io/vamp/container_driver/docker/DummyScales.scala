package io.vamp.container_driver.docker

import com.typesafe.config.ConfigFactory
import io.vamp.model.artifact.DefaultScale
import io.vamp.model.reader.MegaByte

import scala.async.Async.{ async, await }
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

/**
 * Container managers which don't support scaling, need to report back the scale they received during creation / updating a deployment
 * This trait contains a map, in which the scales of each deployment server can be stored.
 */

trait DummyScales {

  private val defaultScale: DefaultScale = {
    val c = ConfigFactory.load().getConfig("vamp.operation.deployment.scale")
    DefaultScale(name = "", cpu = c.getDouble("cpu"), memory = MegaByte.of(c.getString("memory")), instances = c.getInt("instances"))
  }

  private var scales: Map[String, DefaultScale] = Map.empty

  /**
   * Add a scale to the map
   * @param containerId
   * @param scale
   * @param executionContext - Needed to wait for the Future result
   * @return  Returns a Future, so the caller does not have to wait for the result
   */
  protected def addScale(containerId: Future[String], scale: Option[DefaultScale])(implicit executionContext: ExecutionContext) = async {
    val id = await(containerId)
    scale match {
      case Some(sc) ⇒ scales = scales.filter(_._1 != id) + (id -> sc)
      case _        ⇒ scales = scales.filter(_._1 != id)
    }
  }

  /**
   * Remove a scale from the map
   * @param id
   */
  protected def removeScale(id: String): Unit = {
    scales = scales - id
  }

  /**
   * Return a scale
   * If the scale can not be found: Lie
   * @param id
   * @return
   */
  protected def getScale(id: String): DefaultScale = scales.getOrElse(id, defaultScale)

}
