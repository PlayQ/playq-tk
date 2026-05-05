package net.playq.tk.orc

import distage.{DefaultModule2, TagKK}
import izumi.functional.bio.{BlockingIO2, Clock1, F, IO2}
import net.playq.tk.orc.OrcColumn.StringOps
import net.playq.tk.orc.OrcValue.OrcAny
import net.playq.tk.orc.TkOrcTestBase.Ctx
import net.playq.tk.quantified.SyncThrowable
import net.playq.tk.test.{TkTestBaseCtx, WithDummy}
import net.playq.tk.util.ClosableFile
import zio.IO

import java.nio.file.Files
import java.time.ZonedDateTime
import scala.collection.immutable.ArraySeq

final class TkOrcTest extends TkOrcTestBase[IO] with WithDummy

object TkOrcTestBase {
  final case class Ctx[F[+_, +_]](
  )(implicit
    val sync: SyncThrowable[F],
    val blockingIO: BlockingIO2[F],
  )
}

abstract class TkOrcTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {

  "Orc" should {
    "write and read" in bound {
      ctx =>
        import ctx.*
        val schema = OrcSchema.from(
          "bool".of[Boolean],
          "double".of[Double],
          "long".of[Long],
          "map".of[Map[String, Long]],
          "str".of[String],
          "tsz".of[ZonedDateTime],
          "list".of[List[String]],
          "missing".of[List[Double]],
        )
        val partialSchema = OrcSchema.from(
          "double".of[Double],
          "list".of[List[String]],
        )
        val values = ArraySeq(
          Map[String, OrcAny](
            "bool"   -> true,
            "double" -> 1d,
            "long"   -> 1L,
            "map"    -> Map("1" -> 1L),
            "str"    -> "1",
            "tsz"    -> Clock1.Standard.nowZoned(),
            "list"   -> List("1", "2", "3"),
          ),
          Map[String, OrcAny](
            "bool"   -> false,
            "double" -> 2d,
            "long"   -> 2L,
            "map"    -> Map("2" -> 2L, "3" -> 3L),
            "str"    -> "2",
            "list"   -> List("2", "2"),
          ),
          Map[String, OrcAny](
            "bool"   -> true,
            "double" -> 3d,
            "map"    -> Map("3" -> 3L),
            "str"    -> "3",
            "tsz"    -> Clock1.Standard.nowZoned(),
            "list"   -> List("3", "3"),
          ),
        )

        ClosableFile.resourceTempFile().use {
          file =>
            for {
              _        <- OrcWriter.openWithHeader(file.file, schema).use(_.write(values, 1))
              fromOrc  <- OrcReader.open(file.file, schema).use(_.readAllWithHeader(1))
              fromOrc2 <- OrcReader.open(Files.readAllBytes(file.absolutePath), schema).use(_.readAllWithHeader(100, 1))
              _        <- assertIO(fromOrc == fromOrc2)
              _        <- assertIO(fromOrc.flatten.filter(_._1 == "missing").map(_._2) == Vector(OrcValue.OrcNull, OrcValue.OrcNull, OrcValue.OrcNull))
              _        <- assertIO(fromOrc.map(_.filterNot(_._2 == OrcValue.OrcNull)) == values)
              // extract map types
              _ <- assertIO {
                fromOrc.headOption.flatMap(_.toMap.get("map")).flatMap(_.asMap[String, Long]).contains(Map("1" -> 1L))
              }
              _ <- assertIO {
                fromOrc.headOption.flatMap(_.toMap.get("map")).flatMap(_.asMap[Double, Long]).isEmpty
              }
              // extract list types
              _ <- assertIO {
                fromOrc.headOption.flatMap(_.toMap.get("list")).flatMap(_.asIterable[String]).contains(Iterable("1", "2", "3"))
              }
              _ <- assertIO {
                fromOrc.headOption.flatMap(_.toMap.get("list")).flatMap(_.asIterable[Long]).isEmpty
              }

              simpleFilter <- OrcReader.open(file.file, schema).use(_.readAllWithHeader(1, 1, List(OrcRecordFilter("double".of[Double])(_.asDouble.contains(1d)))))
              _            <- assertIO {
                simpleFilter.size == 1 &&
                simpleFilter.headOption.flatMap(_.toMap.get("double")).flatMap(_.asDouble).contains(1d)
              }

              mapFilter <- OrcReader
                .open(file.file, schema)
                .use(_.readAllWithHeader(1000, 100, List(OrcRecordFilter("map".of[Map[String, Long]])(_.asMap[String, Long].exists(_.contains("2"))))))
              _ <- assertIO {
                mapFilter.size == 1 &&
                mapFilter.headOption.flatMap(_.toMap.get("map")).flatMap(_.asMap[String, Long]).contains(Map("2" -> 2L, "3" -> 3L))
              }

              partial <- OrcReader.open(file.file, partialSchema).use(_.readAllWithHeader(100, 1))
              _       <- assertIO(partial.forall(_.size == 2))
            } yield ()
        }
    }

    "write and read without headers" in bound {
      ctx =>
        import ctx.*
        val schema = OrcSchema.from(
          "bool".of[Boolean],
          "double".of[Double],
          "long".of[Long],
          "map".of[Map[String, Long]],
          "str".of[String],
          "tsz".of[ZonedDateTime],
          "list".of[List[String]],
          "missing".of[List[Double]],
        )
        val values = ArraySeq(
          ArraySeq[OrcValue.OrcAny](true, 1d, 1L, Map("1" -> 1L), "1", Clock1.Standard.nowZoned(), List("1", "2", "3"), OrcValue.OrcNull),
          ArraySeq[OrcValue.OrcAny](false, 2d, 2L, Map("2" -> 2L), "2", Clock1.Standard.nowZoned(), List("1", "2", "3"), OrcValue.OrcNull),
          ArraySeq[OrcValue.OrcAny](true, 2d, 3L, Map("3" -> 3L), "3", Clock1.Standard.nowZoned(), List("1", "2", "3"), OrcValue.OrcNull),
        )

        ClosableFile.resourceTempFile().use {
          file =>
            for {
              _        <- OrcWriter.open(file.file, schema).use(_.write(values, 1))
              fromOrc  <- OrcReader.open(file.file, schema).use(_.readAllWithHeader(1))
              fromOrc2 <- OrcReader.open(Files.readAllBytes(file.absolutePath), schema).use(_.readAllWithHeader(100))
              _        <- assertIO(fromOrc == fromOrc2)
              _        <- assertIO(fromOrc.flatten.filter(_._1 == "missing").map(_._2) == Vector(OrcValue.OrcNull, OrcValue.OrcNull, OrcValue.OrcNull))
              fromOrc3 <- OrcReader.open(file.file, schema).use(r => F.sync(r.readUnsafe(1)))
              _        <- assertIO(fromOrc3 == values)
            } yield ()
        }
    }
  }
}
