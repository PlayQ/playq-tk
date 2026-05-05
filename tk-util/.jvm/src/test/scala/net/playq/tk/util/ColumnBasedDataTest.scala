package net.playq.tk.util

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Clock1, F, IO2, Primitives2}
import izumi.fundamentals.platform.time.IzTime
import net.playq.tk.test.rnd.TkRndBase
import net.playq.tk.test.{TkTestBaseCtx, WithDummy}
import net.playq.tk.util.ColumnBasedDataTestBase.Ctx
import net.playq.tk.util.data.{ColumnBasedData, MutableColumnBasedData}
import zio.IO

import java.time.LocalDate

final class ColumnBasedDataTest extends ColumnBasedDataTestBase[IO]

object ColumnBasedDataTestBase {
  final case class Ctx[F[+_, +_]]()(implicit val P: Primitives2[F])
}

abstract class ColumnBasedDataTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with WithDummy with TkRndBase {
  "ColumnBasedData" should {
    "have the same output for mutable and immutable implementation" in bound {
      ctx =>
        import ctx.*
        val now    = Clock1.Standard.nowZoned().format(IzTime.ISO_LOCAL_DATE)
        val header = (0 to 10).map(_.toString).toList
        val data   = (0 to 10).map(r => now :: (1 to 10).map(c => s"$c$r").toList).toList
        val csv    = header :: data
        for {
          immutable <- F.sync(ColumnBasedData.fromCSV(csv))
          mutable   <- F.sync(MutableColumnBasedData.fromCSV(csv))
          _         <- assertIO(immutable.rows.toList == data)
          _         <- assertIO(mutable.rows.toList == data)

          rowsI <- F.mkRef(List.empty[List[String]])
          rowsM <- F.mkRef(List.empty[List[String]])
          _     <- immutable.traverseRows(r => rowsI.update(_.appended(r.toList)).void)
          _     <- mutable.traverseRows(r => rowsM.update(_.appended(r.toList)).void)
          _     <- rowsI.get.flatMap(r => assertIO(r == data))
          _     <- rowsM.get.flatMap(r => assertIO(r == data))

          immutable2     = immutable.updateHeader(s => "#" + s)
          _              = mutable.updateHeader(s => "#" + s)
          updatedHeader1 = header.map("#" + _)
          _             <- assertIO(immutable2.header == updatedHeader1)
          _             <- assertIO(mutable.header == updatedHeader1)

          immutable3     = immutable2.addColumn("testCol", Vector.fill(immutable2.size)("0"))
          _              = mutable.addColumn("testCol", Vector.fill(mutable.size)("0"))
          updatedHeader0 = updatedHeader1.appended("testCol")
          updatedData0   = data.map(_.appended("0"))
          _             <- assertIO(immutable3.header.toList == updatedHeader0)
          _             <- assertIO(mutable.header.toList == updatedHeader0)
          _             <- assertIO(immutable3.rows.toList == updatedData0)
          _             <- assertIO(mutable.rows.toList == updatedData0)

          immutable4 = immutable3.removeColumn("testCol")
          _          = mutable.removeColumn("testCol")
          _         <- assertIO(immutable4.header.toList == updatedHeader1)
          _         <- assertIO(mutable.header.toList == updatedHeader1)
          _         <- assertIO(immutable4.rows.toList == data)
          _         <- assertIO(mutable.rows.toList == data)

          immutable5      = immutable4.addColumns(Seq("testCol1", "testCol2"), Vector(Vector.fill(immutable4.size)("1"), Vector.fill(immutable4.size)("2")))
          _               = mutable.addColumns(Seq("testCol1", "testCol2"), Vector(Vector.fill(immutable4.size)("1"), Vector.fill(immutable4.size)("2")))
          updatedHeader00 = updatedHeader1.appendedAll(Seq("testCol1", "testCol2"))
          updatedData00   = data.map(_.appendedAll(Seq("1", "2")))
          _              <- assertIO(immutable5.header.toList == updatedHeader00)
          _              <- assertIO(mutable.header.toList == updatedHeader00)
          _              <- assertIO(immutable5.rows.toList == updatedData00)
          _              <- assertIO(mutable.rows.toList == updatedData00)

          immutable6 = immutable5.removeColumn("testCol1").removeColumn("testCol2")
          _          = mutable.removeColumn("testCol1").removeColumn("testCol2")

          immutable7     = immutable6.addColumnsRepeated(Map("testMeta" -> "testMetaVal"))
          _              = mutable.addColumnsRepeated(Map("testMeta" -> "testMetaVal"))
          updatedHeader2 = updatedHeader1.appended("testMeta")
          updatedData2   = data.map(_.appended("testMetaVal"))
          _             <- assertIO(immutable7.header.toList == updatedHeader2)
          _             <- assertIO(mutable.header.toList == updatedHeader2)
          _             <- assertIO(immutable7.rows.toList == updatedData2)
          _             <- assertIO(mutable.rows.toList == updatedData2)

          imCol = immutable7.getColumn("#1")
          mCol  = mutable.getColumn("#1")
          _    <- assertIO(imCol.contains((0 to 10).map(i => s"1$i").toVector))
          _    <- assertIO(mCol.contains((0 to 10).map(i => s"1$i").toVector))

          res1 = immutable7.groupBy("#0")(LocalDate.parse(_, IzTime.ISO_LOCAL_DATE))
          res2 = mutable.groupBy("#0")(LocalDate.parse(_, IzTime.ISO_LOCAL_DATE))

          _ <- assertIO(res2.keySet == res1.keySet)
          _ <- assertIO(res1.keySet == Set(LocalDate.parse(now, IzTime.ISO_LOCAL_DATE)))
          _ <- assertIO(res2.keySet == Set(LocalDate.parse(now, IzTime.ISO_LOCAL_DATE)))

          additionalHeader = header.sortBy(_ => random[Int])
          additionalData   = (0 to 10).toList.map(_.toString)
          dataSorted       = header.map(h => additionalData(additionalHeader.indexOf(h)))
          d1               = ColumnBasedData.fromRowsWithHeader(additionalHeader, List(additionalData))
          immutable8       = immutable ++ d1
          mutable2         = MutableColumnBasedData.fromCSV(csv) ++ d1
          _               <- assertIO(immutable8.rows.toList == data ++ List(dataSorted))
          _               <- assertIO(mutable2.rows.toList == data ++ List(dataSorted))
          d2               = ColumnBasedData.fromRowsWithHeader(header, List(additionalData))
          immutable9       = immutable8 ++ d2
          _                = mutable2 ++ d2
          _               <- assertIO(immutable9.rows.toList == data ++ List(dataSorted, additionalData))
          _               <- assertIO(mutable2.rows.toList == data ++ List(dataSorted, additionalData))
        } yield ()
    }

    "join data correctly" in bound {
      _ =>
        F.sync {
          val leftCsv  = List(List("f1", "f2"), List("1", "11"), List("2", "22"))
          val rightCsv = List(List("f2", "f3"), List("11", "111"), List("33", "333"))
          val right    = MutableColumnBasedData.fromCSV(rightCsv)

          val result1 = MutableColumnBasedData.fromCSV(leftCsv).leftJoin(right)("", "f2", "f2", "left", "right")
          assert(result1.header == Vector("left_f1", "left_f2", "right_f3"))

          val result2 = MutableColumnBasedData.empty(Seq("f1", "f2")).leftJoin(right)("", "f2", "f2", "left", "right")
          assert(result2.header == Vector("left_f1", "left_f2", "right_f3"))

          val result3 = MutableColumnBasedData.empty(Seq()).leftJoin(right)("", "f2", "f2", "left", "right")
          assert(result3.header == Vector("right_f3"))
        }
    }
  }
}
