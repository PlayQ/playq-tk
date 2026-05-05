package net.playq.tk.postgres.codecs

import cats.Show
import cats.data.NonEmptyList
import cats.implicits.*
import doobie.enumerated.JdbcType
import doobie.postgres.Instances
import io.circe.jawn.parse
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json, Printer}
import doobie.util.*
import doobie.util.meta.Meta
import izumi.functional.bio.Clock1
import org.postgresql.util.PGobject
import org.tpolecat.typename.TypeName

import java.time.*
import scala.util.chaining.*

trait TkPostgresInstances {
  this: Instances =>

  private implicit lazy val showPGobject: Show[PGobject] = Show.show(_.getValue.take(250))

  /** PG supports Java 8 types directly https://jdbc.postkresql.org/documentation/head/8-date-time.html */
  implicit final lazy val OffsetDateTimeMeta: Meta[OffsetDateTime] = Meta.Basic.one(
    jdbcType            = JdbcType.TimestampWithTimezone,
    jdbcSourceSecondary = Nil,
    get                 = (r, i) => r.getObject(i, classOf[OffsetDateTime]),
    put                 = (p, i, a) => p.setObject(i, a, JdbcType.TimestampWithTimezone.toInt),
    update              = (r, i, a) => r.updateObject(i, a, JdbcType.TimestampWithTimezone.toInt),
  )

  /** PG supports Java 8 types directly https://jdbc.postkresql.org/documentation/head/8-date-time.html */
  implicit final lazy val LocalDateTimeMeta: Meta[LocalDateTime] = Meta.Basic.one(
    jdbcType            = JdbcType.Timestamp,
    jdbcSourceSecondary = Nil,
    get                 = (r, i) => r.getObject(i, classOf[LocalDateTime]),
    put                 = (p, i, a) => p.setObject(i, a, JdbcType.Timestamp.toInt),
    update              = (r, i, a) => r.updateObject(i, a, JdbcType.Timestamp.toInt),
  )

  /** Legacy LocalDate meta copy from doobie implicits. */
  implicit final lazy val LocalDateMeta: Meta[LocalDate] = {
    Meta.DateMeta.imap(_.toLocalDate)(java.sql.Date.valueOf)
  }

  /** PG supports Java 8 types directly https://jdbc.postkresql.org/documentation/head/8-date-time.html */
  implicit final lazy val ZonedDateTimeMeta: Meta[ZonedDateTime] = {
    OffsetDateTimeMeta.timap(_.atZoneSameInstant(Clock1.TZ_UTC))(_.toOffsetDateTime.withOffsetSameInstant(ZoneOffset.UTC))
  }

  implicit final lazy val tkJsonbPut: Put[Json] = {
    val jsonbPrinter = Printer.noSpaces.copy(dropNullValues = true)
    Put.Advanced.other[PGobject](NonEmptyList.of("jsonb")).tcontramap {
      a =>
        new PGobject()
          .tap(_.setType("jsonb"))
          .tap(_.setValue(jsonbPrinter.print(a)))
    }
  }

  implicit final lazy val tkJsonbGet: Get[Json] = {
    Get.Advanced
      .other[PGobject](NonEmptyList.of("jsonb"))
      .temap(a => parse(a.getValue).leftMap(_.show))
  }

  implicit final lazy val JsonMeta: Meta[Json] = new Meta[Json](tkJsonbGet, tkJsonbPut)

  final def metaFromJson[T: Encoder: Decoder: TypeName]: Meta[T] = {
    val get = getFromJson[T]
    val put = putFromJson[T]
    new Meta[T](get, put)
  }

  final def getFromJson[T: Decoder: TypeName]: Get[T] = {
    tkJsonbGet.temap(_.as[T].left.map(_.getMessage()))
  }

  final def putFromJson[T: Encoder: TypeName]: Put[T] = {
    tkJsonbPut.tcontramap[T](_.asJson)
  }

  /** To use hstore it should be enabled, by default we wil use JSONB instance for string maps. */
  override implicit final val hstoreMeta: Meta[Map[String, String]] = metaFromJson
}
