package io.vamp.config

import io.vamp.config.custom.NamespaceString
import ConfigSettings.defaultSettings
import io.vamp.common.Namespace
import io.vamp.config.Config._

import scala.concurrent.duration.Duration
import io.vamp.config.ConfigReader._
import io.vamp.config.ConfigPersistence._

object VAMPConfig {

  implicit val namespace: Namespace = ??? // TODO

  val retrieveNamespace: ConfigResult[String] = Config.read[String]("vamp.namespace")

  val useDBConfig: ConfigResult[Boolean] = Config.read[Boolean]("vamp.use-db-config")

  def persistenceConfig: ConfigResult[PersistenceConfig] = Config.read[PersistenceConfig]("vamp.persistence")

  def containerDriverConfig: ConfigResult[ContainerDriverConfig] =
    Config.retrieve[ContainerDriverConfig]("container-driver") <|> Config.read[ContainerDriverConfig]("vamp.container-driver")

  def httpAPIConfig: ConfigResult[HttpApiConfig] =
    Config.retrieve[HttpApiConfig]("http-api") <|> Config.read[HttpApiConfig]("vamp.http-api")

  def workflowDriverConfig: ConfigResult[WorkflowDriverConfig] =
    Config.retrieve[WorkflowDriverConfig]("workflow-driver") <|> Config.read[WorkflowDriverConfig]("vamp.config-driver")

  def pulseConfig: ConfigResult[PulseConfig] =
    Config.retrieve[PulseConfig]("pulse") <|> Config.read[PulseConfig]("vamp.pulse")

  def gatewayDriver: ConfigResult[GatewayDriverConfig] =
    Config.retrieve[GatewayDriverConfig]("gateway-driver") <|> Config.read[GatewayDriverConfig]("vamp.gateway-driver")

  def operationConfig: ConfigResult[OperationConfig] =
    Config.retrieve[OperationConfig]("operation") <|> Config.read[OperationConfig]("vamp.operation")

  def lifterConfig: ConfigResult[LifterConfig] =
    Config.retrieve[LifterConfig]("lifter") <|> Config.read[LifterConfig]("vamp.lifter")

}

/**
 * Configuration values for database persistence (artifacts) and key-value-store.
 */
case class PersistenceConfig(database: DatabaseConfig, keyValueStore: KeyValueStoreConfig)
object PersistenceConfig {
  implicit val configReader: ConfigReader[PersistenceConfig] = ConfigReader.apply[PersistenceConfig]
}

/**
 * Configuration for databases (in-memory, mysql, postgresql, sqlserver, etc.).
 */
sealed trait DatabaseConfig
object DatabaseConfig {
  implicit val configReader: ConfigReader[DatabaseConfig] = ConfigReader.apply[DatabaseConfig]
}

case class InMemoryDatabaseConfig(`type`: String) extends DatabaseConfig

object InMemoryDatabaseConfig {
  implicit val configReader: ConfigReader[InMemoryDatabaseConfig] = ConfigReader.apply[InMemoryDatabaseConfig]
}

case class SQLDatabaseConfig(`type`: String, sql: SQLConfig) extends DatabaseConfig

object SQLDatabaseConfig {
  implicit val configReader: ConfigReader[SQLDatabaseConfig] = ConfigReader.apply[SQLDatabaseConfig]
}

/**
 * Configuration for connecting to SQL databases.
 */
case class SQLConfig(
  url:             NamespaceString,
  user:            String,
  password:        String,
  delay:           Duration,
  synchronization: SynchronizationSQLConfig)

object SQLConfig {
  implicit val configReader: ConfigReader[SQLConfig] = ConfigReader.apply[SQLConfig]
}

/**
 * Configuration for synchronization time between in-memory and SQL persistence.
 */
case class SynchronizationSQLConfig(period: Duration)

object SynchronizationSQLConfig {
  implicit val configReader: ConfigReader[SynchronizationSQLConfig] = ConfigReader.apply[SynchronizationSQLConfig]
}

/**
 *
 */
sealed trait KeyValueStoreConfig

object KeyValueStoreConfig {
  implicit val configReader: ConfigReader[KeyValueStoreConfig] = ConfigReader.apply[KeyValueStoreConfig]
}

case class ZookeeperKeyValueConfig(`type`: String, zookeeper: ZookeeperConfig) extends KeyValueStoreConfig

object ZookeeperKeyValueConfig {
  implicit val configReader: ConfigReader[ZookeeperKeyValueConfig] = ConfigReader.apply[ZookeeperKeyValueConfig]
}

case class ETCDKeyValueConfig() extends KeyValueStoreConfig

object ETCDKeyValueConfig {
  implicit val configReader: ConfigReader[ETCDKeyValueConfig] = ConfigReader.apply[ETCDKeyValueConfig]
}

/**
 *
 */
case class ZookeeperConfig(servers: String)

object ZookeeperConfig {
  implicit val configReader: ConfigReader[ZookeeperConfig] = ConfigReader.apply[ZookeeperConfig]
}

/**
 *
 */
sealed trait ContainerDriverConfig

object ContainerDriverConfig {
  implicit val configReader: ConfigReader[ContainerDriverConfig] = ConfigReader.apply[ContainerDriverConfig]
}

case class MarathonContainerDriverConfig(mesos: MesosConfig, marathon: MarathonConfig)

object MarathonContainerDriverConfig {
  implicit val configReader: ConfigReader[MarathonContainerDriverConfig] =
    ConfigReader.apply[MarathonContainerDriverConfig]
}

/**
 *
 */
case class MesosConfig(url: String)

object MesosConfig {
  implicit val configReader: ConfigReader[MesosConfig] = ConfigReader.apply[MesosConfig]
}

case class MarathonConfig(url: String)

object MarathonConfig {
  implicit val configReader: ConfigReader[MarathonConfig] = ConfigReader.apply[MarathonConfig]
}

/**
 *
 */
case class HttpApiConfig(ui: UIConfig)

object HttpApiConfig {
  implicit val configReader: ConfigReader[HttpApiConfig] = ConfigReader.apply[HttpApiConfig]
}

case class UIConfig(directory: String, index: String)

object UIConfig {
  implicit val configReader: ConfigReader[UIConfig] = ConfigReader.apply[UIConfig]
}

/**
 *
 */
case class WorkflowDriverConfig(`type`: String, chronos: ChronosConfig, workflow: WorkflowConfig)

object WorkflowDriverConfig {
  implicit val configReader: ConfigReader[WorkflowDriverConfig] = ConfigReader.apply[WorkflowDriverConfig]
}

case class ChronosConfig(url: String)

object ChronosConfig {
  implicit val configReader: ConfigReader[ChronosConfig] = ConfigReader.apply[ChronosConfig]
}

/**
 *
 */
case class WorkflowConfig(
  deployables:                     List[DeployableConfig],
  scale:                           ScaleConfig,
  vampUrl:                         String,
  vampKeyValueStorePath:           String,
  vampKeyValueStoreType:           String,
  vampVampKeyValueStoreConnection: String,
  vampWorkflowPeriodTimeout:       Int,
  vampWorkflowExecutionTimeout:    Int,
  vampElasticsearchUrl:            String)

object WorkflowConfig {
  implicit val configReader: ConfigReader[WorkflowConfig] = ConfigReader.apply[WorkflowConfig]
}

/**
 *
 */
case class DeployableConfig(`type`: String, breed: String)

object DeployableConfig {
  implicit val configReader: ConfigReader[DeployableConfig] = ConfigReader.apply[DeployableConfig]
}

/**
 *
 */
case class PulseConfig(`type`: String, elasticsearch: ElasticSearchPulseConfig)

object PulseConfig {
  implicit val configReader: ConfigReader[PulseConfig] = ConfigReader.apply[PulseConfig]
}

case class ElasticSearchPulseConfig(url: String)

object ElasticSearchPulseConfig {
  implicit val configReader: ConfigReader[ElasticSearchPulseConfig] = ConfigReader.apply[ElasticSearchPulseConfig]
}

/**
 *
 */
case class GatewayDriverConfig(elasticsearch: GatewayESConfig, marshallers: List[MarshallerConfig])

object GatewayDriverConfig {
  implicit val configReader: ConfigReader[GatewayDriverConfig] = ConfigReader.apply[GatewayDriverConfig]
}

/**
 *
 */
case class GatewayESConfig(metrics: GatewayMetricsConfig)

object GatewayESConfig {
  implicit val configReader: ConfigReader[GatewayESConfig] = ConfigReader.apply[GatewayESConfig]
}

case class GatewayMetricsConfig(index: String, `type`: String)

object GatewayMetricsConfig {
  implicit val configReader: ConfigReader[GatewayMetricsConfig] = ConfigReader.apply[GatewayMetricsConfig]
}

/**
 *
 */
case class MarshallerConfig(`type`: String, name: String, template: TemplateConfig)

object MarshallerConfig {
  implicit val configReader: ConfigReader[MarshallerConfig] = ConfigReader.apply[MarshallerConfig]
}

case class TemplateConfig(resource: String)

object TemplateConfig {
  implicit val configReader: ConfigReader[TemplateConfig] = ConfigReader.apply[TemplateConfig]
}

/**
 *
 */
case class OperationConfig(synchronization: SynchronizationConfig, deployment: DeploymentConfig)

object OperationConfig {
  implicit val configReader: ConfigReader[OperationConfig] = ConfigReader.apply[OperationConfig]
}

/**
 *
 */
case class SynchronizationConfig(period: Duration, check: CheckConfig)

object SynchronizationConfig {
  implicit val configReader: ConfigReader[SynchronizationConfig] = ConfigReader.apply[SynchronizationConfig]
}

case class CheckConfig(cpu: Boolean, memory: Boolean, instances: Boolean, healthChecks: Boolean)

object CheckConfig {
  implicit val configReader: ConfigReader[CheckConfig] = ConfigReader.apply[CheckConfig]
}

/**
 *
 */
case class DeploymentConfig(scale: ScaleConfig, arguments: List[String])

object DeploymentConfig {
  implicit val configReader: ConfigReader[DeploymentConfig] = ConfigReader.apply[DeploymentConfig]
}

case class ScaleConfig(instances: Int, cpu: Double, memory: String)

object ScaleConfig {
  implicit val configReader: ConfigReader[ScaleConfig] = ConfigReader.apply[ScaleConfig]
}

/**
 *
 */
case class LifterConfig(artifact: ArtifactConfig, sql: Option[SqlLifterConfig])

object LifterConfig {
  implicit val configReader: ConfigReader[LifterConfig] = ConfigReader.apply[LifterConfig]
}

case class ArtifactConfig(files: List[String])

object ArtifactConfig {
  implicit val configReader: ConfigReader[ArtifactConfig] = ConfigReader.apply[ArtifactConfig]
}

case class SqlLifterConfig(connection: DbConnectionConfig, database: String, user: String, url: String)

object SqlLifterConfig {
  implicit val configReader: ConfigReader[SqlLifterConfig] = ConfigReader.apply[SqlLifterConfig]
}

case class DbConnectionConfig(tableUrl: String, databaseUrl: String)

object DbConnectionConfig {
  implicit val configReader: ConfigReader[DbConnectionConfig] = ConfigReader.apply[DbConnectionConfig]
}
