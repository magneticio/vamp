package io.magnetic.vamp_core.persistence.slick.extension

import io.strongtyped.active.slick.exceptions.{NoRowsAffectedException, RowNotFoundException}
import io.strongtyped.active.slick.{Profile, TableQueries, Tables}

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}


trait VampTableQueries extends TableQueries with VampTables {
  this: Profile with Tables =>

  import jdbcDriver.simple._


  class NameableEntityTableQuery[M <: Nameable[M], T <: NameableEntityTable[M]](cons: Tag => T)(implicit ev1: BaseColumnType[M#Id])
    extends EntityTableQuery[M, T](cons) {

    def filterByName(name: String)(implicit sess: Session) = filter(_.name === name)

    def deleteByName(name: String)(implicit sess: Session): Unit = tryDeleteByName(name).get

    def tryDeleteByName(name: String)(implicit sess: Session): Try[Unit] = {
      rollbackOnFailure {
        mustAffectOneSingleRow {
          filterByName(name).delete
        }.recoverWith {
          case NoRowsAffectedException => Failure(RowNotFoundException(name))
        }
      }
    }

    def tryFindByName(name: String)(implicit sess: Session): Try[M] = {
      findOptionByName(name) match {
        case Some(model) => Success(model)
        case None => Failure(RowNotFoundException(name))
      }
    }

    def findByName(name: String)(implicit sess: Session): M = findOptionByName(name).get

    def findOptionByName(name: String)(implicit sess: Session): Option[M] = filterByName(name).firstOption
  }

  object NameableEntityTableQuery {
    def apply[M <: Nameable[M], T <: NameableEntityTable[M]](cons: Tag => T)(implicit ev1: BaseColumnType[M#Id]) =
      new NameableEntityTableQuery[M, T](cons)
  }

  class AnonymousNameableEntityTableQuery[M <: AnonymousNameable[M], T <: AnonymousNameableEntityTable[M]](cons: Tag => T)(implicit ev1: BaseColumnType[M#Id])
    extends NameableEntityTableQuery[M, T](cons) {

    // Remap the 'fetch list' methods to exclude the anonymous rows
    override def fetchAll(implicit sess: Session): List[M] = this.filter(m=> m.isAnonymous === false ).list
    override def pagedList(pageIndex: Int, limit: Int)(implicit sess: Session): List[M] =
      filter(m=> m.isAnonymous === false).drop(pageIndex).take(limit).run.toList

    // Map the original 'fetch list' methods to other method names. Can be used for inspecting the database contents
    def fetchAllIncludeAnonymous(implicit sess: Session): List[M] = super.fetchAll
    def pagedListIncludeAnonymous(pageIndex: Int, limit: Int)(implicit sess: Session): List[M] = super.pagedList(pageIndex, limit)


    // Override the 'anonymous' name, to prevent name classes in a unique constraint
    override def tryAdd(model: M)(implicit sess: Session): Try[M#Id] = {
      rollbackOnFailure {
        if (model.isAnonymous)
          Try(this.returning(this.map(_.id)).insert(model.withAnonymousName))
        else
        Try(this.returning(this.map(_.id)).insert(model))
      }
    }
  }

  object AnonymousNameableEntityTableQuery {
    def apply[M <: AnonymousNameable[M], T <: AnonymousNameableEntityTable[M]](cons: Tag => T)(implicit ev1: BaseColumnType[M#Id]) =
      new AnonymousNameableEntityTableQuery[M, T](cons)
  }

}