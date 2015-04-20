package io.vamp.core.persistence.slick.components

import java.sql.Timestamp

import io.strongtyped.active.slick.Profile
import io.vamp.core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.vamp.core.persistence.slick.model.VampPersistenceMetaDataModel

import scala.language.implicitConversions
import scala.slick.jdbc.meta.MTable
import scala.slick.util.Logging

trait Schema extends Logging with SchemaBreed with SchemaBlueprint with SchemaDeployment {
  this: VampTables with VampTableQueries with Profile =>

  import jdbcDriver.simple._

  val VampPersistenceMetaDatas = EntityTableQuery[VampPersistenceMetaDataModel, VampPersistenceMetaDataTable](tag => new VampPersistenceMetaDataTable(tag))

  def upgradeSchema(implicit sess: Session) = {
    getCurrentSchemaVersion match {
      case version if version == schemaVersion =>
      // Schema is up-to-date
      case version if version == 0 =>
        createSchema
    }
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

  private def schemaVersion: Int = 1

  private def metaDataTableName: String = "vamp-meta-data"

  private def getCurrentSchemaVersion(implicit sess: Session): Int =
    MTable.getTables(metaDataTableName).firstOption match {
      case Some(_) => VampPersistenceMetaDatas.sortBy(_.id.desc).firstOption match {
        case Some(metaData) => metaData.schemaVersion
        case None => 0
      }
      case None => 0
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
