package io.vamp.persistence.sqlconnectionpool

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.dbcp2._

protected case class DataSourceConfiguration(url: String, user: String, password: String)

object ConnectionPool extends LazyLogging {

  protected val dataSources = scala.collection.concurrent.TrieMap[DataSourceConfiguration, BasicDataSource]()

  def apply(url: String, user: String, password: String): BasicDataSource = this.synchronized {
    val conf = DataSourceConfiguration(url, user, password)
    dataSources.getOrElseUpdate(conf, {
      logger.info(s"create DataSource ${conf.url}")
      val datasource = new BasicDataSource()
      datasource.setUsername(user)
      datasource.setPassword(password)
      datasource.setUrl(url)
      datasource
    })
  }

}
