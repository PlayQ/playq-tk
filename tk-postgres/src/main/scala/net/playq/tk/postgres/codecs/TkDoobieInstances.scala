package net.playq.tk.postgres.codecs

import cats.Show
import cats.data.NonEmptyList
import cats.implicits.*
import doobie.enumerated.JdbcType
import doobie.postgres.Instances
import doobie.util.meta.Meta
import doobie.util.{Get, Put}
import io.circe.jawn.parse
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json, Printer}
import izumi.fundamentals.platform.time.IzTime
import org.postgresql.util.PGobject
import org.tpolecat.typename.TypeName

import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset, ZonedDateTime}
import scala.util.chaining.*

trait TkDoobieInstances {
  this: Instances =>

  private implicit val showPGobject: Show[PGobject] = Show.show(_.getValue.take(250))

  /** PG supports Java 8 types directly https://jdbc.postkresql.org/documentation/head/8-date-time.html */
  implicit final val OffsetDateTimeMeta: Meta[OffsetDateTime] =
    Meta.Basic.one(
      jdbcType            = JdbcType.TimestampWithTimezone,
      jdbcSourceSecondary = Nil,
      get                 = (r, i) => r.getObject(i, classOf[OffsetDateTime]),
      put                 = (p, i, a) => p.setObject(i, a, JdbcType.TimestampWithTimezone.toInt),
      update              = (r, i, a) => r.updateObject(i, a, JdbcType.TimestampWithTimezone.toInt),
    )

  /** PG supports Java 8 types directly https://jdbc.postkresql.org/documentation/head/8-date-time.html */
  implicit final val LocalDateTimeMeta: Meta[LocalDateTime] =
    Meta.Basic.one(
      jdbcType            = JdbcType.Timestamp,
      jdbcSourceSecondary = Nil,
      get                 = (r, i) => r.getObject(i, classOf[LocalDateTime]),
      put                 = (p, i, a) => p.setObject(i, a, JdbcType.Timestamp.toInt),
      update              = (r, i, a) => r.updateObject(i, a, JdbcType.Timestamp.toInt),
    )

  /** PG supports Java 8 types directly https://jdbc.postkresql.org/documentation/head/8-date-time.html */
  implicit final val ZonedDateTimeMeta: Meta[ZonedDateTime] =
    OffsetDateTimeMeta.timap(_.atZoneSameInstant(IzTime.TZ_UTC))(_.toOffsetDateTime.withOffsetSameInstant(ZoneOffset.UTC))

  implicit val tkJsonbPut: Put[Json] = {
    val jsonbPrinter = Printer.noSpaces.copy(dropNullValues = true)
    Put.Advanced.other[PGobject](NonEmptyList.of("jsonb")).tcontramap {
      a =>
        new PGobject()
          .tap(_.setType("jsonb"))
          .tap(_.setValue(jsonbPrinter.print(a)))
    }
  }

  implicit val tkJsonbGet: Get[Json] =
    Get.Advanced
      .other[PGobject](
        NonEmptyList.of("jsonb")
      ).temap(a => parse(a.getValue).leftMap(_.show))

  implicit val JsonMeta: Meta[Json] = new Meta[Json](tkJsonbGet, tkJsonbPut)

  def metaFromJson[T: Encoder: Decoder: TypeName]: Meta[T] = {
    val get = tkJsonbGet.temap(_.as[T].left.map(_.getMessage()))
    val put = tkJsonbPut.tcontramap[T](_.asJson)
    new Meta[T](get, put)
  }

}
