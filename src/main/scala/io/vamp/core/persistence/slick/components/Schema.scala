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
      logger.info(tableQuery.ddl.createStatements.mkString)
      tableQuery.ddl.create
    }
    VampPersistenceMetaDatas.add(VampPersistenceMetaDataModel(schemaVersion = schemaVersion))
    logger.info("Schema created")
  }

  def destroySchema(implicit sess: Session) = {
    if (getCurrentSchemaVersion == schemaVersion) {
      logger.info("Removing everything from the schema ...")
      for (tableQuery <- tableQueries.reverse) {
        logger.info(tableQuery.ddl.dropStatements.mkString)
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
    TraitNameParameters,
    DefaultFilters,
    DefaultRoutings,
    FilterReferences,
    RoutingReferences,
    DefaultSlas,
    SlaReferences,
    DefaultScales,
    DefaultEscalations,
    EscalationReferences,
    ScaleReferences,
    Dependencies,
    DefaultBreeds,
    BreedReferences,
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

  private def getCurrentSchemaVersion(implicit sess: Session): Int =
    MTable.getTables("vamp-meta-data").firstOption match {
      case Some(_) => VampPersistenceMetaDatas.sortBy(_.id.desc).firstOption match {
        case Some(metaData) => metaData.schemaVersion
        case None => 0
      }
      case None => 0
    }

  def totalNumberOfRowsInDB(implicit sess: Session) : Int =
    tableQueries.map(query => query.fetchAll.length).sum


  class VampPersistenceMetaDataTable(tag: Tag) extends EntityTable[VampPersistenceMetaDataModel](tag, "vamp-meta-data") {
    def * = (id.?, schemaVersion, created) <>(VampPersistenceMetaDataModel.tupled, VampPersistenceMetaDataModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def schemaVersion = column[Int]("schema_version")

    def created = column[Timestamp]("created")
  }


}
