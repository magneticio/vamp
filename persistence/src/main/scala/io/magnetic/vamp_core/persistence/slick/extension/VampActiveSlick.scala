package io.magnetic.vamp_core.persistence.slick.extension

import io.strongtyped.active.slick.{TableQueries, ActiveRecordExtensions, Profile}

trait VampActiveSlick extends scala.AnyRef with VampTables with VampTableQueries with ActiveRecordExtensions with Profile {
}