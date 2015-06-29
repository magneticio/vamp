package io.vamp.core.persistence.slick.components

import java.sql.Timestamp

import io.strongtyped.active.slick.Profile
import io.vamp.core.persistence.notification.{PersistenceNotificationProvider, PersistenceOperationFailure}
import io.vamp.core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.vamp.core.persistence.slick.model.VampPersistenceMetaDataModel

import scala.language.implicitConversions
import scala.slick.jdbc.meta.MTable
import scala.slick.util.Logging

trait Schema extends Logging with SchemaBreed with SchemaBlueprint with SchemaDeployment with PersistenceNotificationProvider {
  this: VampTables with VampTableQueries with Profile =>

  import jdbcDriver.simple._

  val VampPersistenceMetaDatas = EntityTableQuery[VampPersistenceMetaDataModel, VampPersistenceMetaDataTable](tag => new VampPersistenceMetaDataTable(tag))

  def upgradeSchema(implicit sess: Session) = {
    getCurrentSchemaVersion match {
      case version if version == schemaVersion =>
      // Schema is up-to-date
      case version if version == 0 =>
        createSchema
      case version if version == 1 =>
        logger.info("Your database is outdated, automatic migration not supported. Please drop and recreate your database")
        throwException(PersistenceOperationFailure("Your database is outdated, automatic migration not supported. Please drop and recreate your database"))
    }
  }

  def schemaInfo(implicit sess: Session): String =
    geSchemaData match {
      case Some(meta) if meta.schemaVersion == schemaVersion => s"V${meta.schemaVersion} / ${meta.created} [Up to date]"
      case Some(meta)  => s"V${meta.schemaVersion} / ${meta.created} [Outdated]"
      case None=> "Not present"
    }


  private def createSchema(implicit sess: Session) = {
    logger.info("Creating schema ...")
    for (tableQuery <- tableQueries) {
      logger.debug(tableQuery.ddl.createStatements.mkString)
      tableQuery.ddl.create
    }
    VampPersistenceMetaDatas.add(VampPersistenceMetaDataModel(schemaVersion = schemaVersion))
    logger.info("Schema created")
  }

  def destroySchema(implicit sess: Session) = {
    if (getCurrentSchemaVersion == schemaVersion) {
      logger.info("Removing everything from the schema ...")
      for (tableQuery <- tableQueries.reverse) {
        logger.debug(tableQuery.ddl.dropStatements.mkString)
        tableQuery.ddl.drop
      }
      logger.info("Schema cleared")
    }
  }

  private def tableQueries = List(
    Deployments,
    Ports,
    EnvironmentVariables,
    Parameters,
    DeploymentHosts,
    DefaultFilters,
    DefaultRoutings,
    FilterReferences,
    RoutingReferences,
    GenericSlas,
    SlaReferences,
    DefaultScales,
    GenericEscalations,
    EscalationReferences,
    ScaleReferences,
    DefaultBreeds,
    BreedReferences,
    Dependencies,
    ModelConstants,
    DefaultBlueprints,
    Clusters,
    Services,
    BlueprintReferences,
    DeploymentClusters,
    DeploymentServices,
    DeploymentServiceDependencies,
    DeploymentServers,
    ServerPorts,
    ClusterRoutes,
    VampPersistenceMetaDatas
  )

  private def schemaVersion: Int = 2

  private def metaDataTableName: String = "vamp-meta-data"

  private def getCurrentSchemaVersion(implicit sess: Session): Int =
    geSchemaData match {
      case Some(metaData) => metaData.schemaVersion
      case None => 0
    }

  private def geSchemaData(implicit sess: Session): Option[VampPersistenceMetaDataModel] =
    MTable.getTables(metaDataTableName).firstOption match {
      case Some(_) => VampPersistenceMetaDatas.sortBy(_.id.desc).firstOption
      case None => None
    }


  def totalNumberOfRowsInDB(implicit sess: Session): Int =
    tableQueries.map(query => query.fetchAll.length).sum


  class VampPersistenceMetaDataTable(tag: Tag) extends EntityTable[VampPersistenceMetaDataModel](tag, metaDataTableName) {
    def * = (id.?, schemaVersion, created) <>(VampPersistenceMetaDataModel.tupled, VampPersistenceMetaDataModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def schemaVersion = column[Int]("schema_version")

    def created = column[Timestamp]("created")
  }


}
