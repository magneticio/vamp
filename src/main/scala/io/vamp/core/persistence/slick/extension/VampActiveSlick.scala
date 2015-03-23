package io.vamp.core.persistence.slick.extension

import io.strongtyped.active.slick.{ActiveRecordExtensions, Profile}

trait VampActiveSlick extends scala.AnyRef with VampTables with VampTableQueries with ActiveRecordExtensions with Profile {
}