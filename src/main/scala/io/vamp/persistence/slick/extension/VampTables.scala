package io.vamp.persistence.slick.extension

import io.strongtyped.active.slick.{ Profile, Tables }

/**
 * Defines table extension on top of the ActiveSlick extensions
 */

trait VampTables extends Tables {
  this: Profile ⇒

  import jdbcDriver.simple._

  trait NameableColumn {
    def name: Column[String]
  }

  abstract class NameableTable[M, I](tag: Tag, schemaName: Option[String], tableName: String)(override implicit val colType: BaseColumnType[I])
      extends IdTable[M, I](tag, schemaName, tableName)(colType) with NameableColumn {

    def this(tag: Tag, tableName: String)(implicit mapping: BaseColumnType[I]) = this(tag, None, tableName)
  }

  type NameableEntityTable[M <: Nameable[M]] = NameableTable[M, M#Id]

  trait DeployableColumn {
    def deploymentId: Column[Option[Int]]
  }

  abstract class NamedDeployableTable[M, I](tag: Tag, schemaName: Option[String], tableName: String)(override implicit val colType: BaseColumnType[I])
      extends NameableTable[M, I](tag, schemaName, tableName)(colType) with DeployableColumn {

    def this(tag: Tag, tableName: String)(implicit mapping: BaseColumnType[I]) = this(tag, None, tableName)
  }

  type DeployableEntityTable[M <: NamedDeployable[M]] = NamedDeployableTable[M, M#Id]

  trait IsAnonymousColumn {
    def isAnonymous: Column[Boolean]
  }

  abstract class AnonymousDeployablebleTable[M, I](tag: Tag, schemaName: Option[String], tableName: String)(override implicit val colType: BaseColumnType[I])
      extends NamedDeployableTable[M, I](tag, schemaName, tableName)(colType) with IsAnonymousColumn {

    def this(tag: Tag, tableName: String)(implicit mapping: BaseColumnType[I]) = this(tag, None, tableName)
  }

  type AnonymousNameableEntityTable[M <: AnonymousDeployable[M]] = AnonymousDeployablebleTable[M, M#Id]

}

