package io.magnetic.vamp_core.persistence.slick.extension

import io.strongtyped.active.slick.{Profile, Tables}

/**
 * Defines table extension on top of the ActiveSlick extensions
 */

trait VampTables extends Tables {
  this: Profile =>

  import jdbcDriver.simple._

  trait NameColumn {
    def name: Column[String]
  }

  abstract class IdNameableTable[M, I](tag: Tag, schemaName: Option[String], tableName: String)(override implicit val colType: BaseColumnType[I])
    extends IdTable[M, I](tag, schemaName, tableName)(colType) with NameColumn {

    def this(tag: Tag, tableName: String)(implicit mapping: BaseColumnType[I]) = this(tag, None, tableName)
  }

  type NameableEntityTable[M <: Nameable[M]] = IdNameableTable[M, M#Id]

  trait IsAnonymousColumn {
    def isAnonymous: Column[Boolean]
  }

  abstract class AnonymousNameableTable[M, I](tag: Tag, schemaName: Option[String], tableName: String)(override implicit val colType: BaseColumnType[I])
    extends IdNameableTable[M, I](tag, schemaName, tableName)(colType) with IsAnonymousColumn {

    def this(tag: Tag, tableName: String)(implicit mapping: BaseColumnType[I]) = this(tag, None, tableName)
  }

  type AnonymousNameableEntityTable[M <: AnonymousNameable[M]] = AnonymousNameableTable[M, M#Id]

}

