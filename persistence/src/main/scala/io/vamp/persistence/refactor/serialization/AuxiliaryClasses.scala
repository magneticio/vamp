package io.vamp.persistence.refactor.serialization

import java.time.ZoneOffset

import io.circe._
import io.vamp.common.Id
/**
 * Created by mihai on 11/21/17.
 */
case class SerializationSpecifier[T](encoder: Encoder[T], decoder: Decoder[T], typeName: String, idExtractor: T ⇒ Id[T])

private[serialization] case class Period_AuxForSerialazation(years: Int, months: Int, days: Int)
private[serialization] case class Duration_AuxForSerialazation(seconds: Long, nanos: Int)
private[serialization] case class OffsetDateTime_AuzForSerialization(year: Int, month: Int, dayOfMonth: Int,
                                                      hour: Int, minute: Int, second: Int, nanoOfSecond: Int, offset: ZoneOffset)

private[serialization] case class TimeUnit_AuxForSerialization(length: Long, unit: scala.concurrent.duration.TimeUnit)