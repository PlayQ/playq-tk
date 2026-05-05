package net.playq.tk.orc

import net.playq.tk.orc.OrcValue.OrcAny
import org.apache.orc.TypeDescription
import org.apache.orc.TypeDescription.Category

import java.time.OffsetDateTime
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters.IterableHasAsScala

trait OrcSchema {
  def columns: ArraySeq[OrcColumn]

  def columnsByNameUnordered: collection.Map[String, OrcColumn]
  def columnsByIndexUnordered: collection.Map[Int, OrcColumn]

  def typeDescription: TypeDescription

  def indexColumnsFor(desc: TypeDescription): Either[OrcException.SchemaException, ArraySeq[(OrcColumn, Int)]]
}

object OrcSchema {
  def apply(columns: Seq[OrcColumn]): OrcSchema.Simple = new OrcSchema.Simple(columns)
  def from(column: OrcColumn*): OrcSchema.Simple       = new OrcSchema.Simple(column)

  def withEncoder[Row: OrcEncoder](columns: Seq[OrcColumn]): OrcSchema.Typed[Row] = new OrcSchema.Typed[Row](columns)

  // @formatter:off
  import net.playq.tk.orc.OrcColumn.Typed as OCT
  def typed[T1](f1: OCT[T1]): OrcSchema.Typed[T1] = tpd(f1)(r => ArraySeq(f1.tpe.valueOf(r)))
  def typed[T1, T2](f1: OCT[T1], f2: OCT[T2]): OrcSchema.Typed[(T1, T2)] = tpd(f1, f2)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2)))
  def typed[T1, T2, T3](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3]): OrcSchema.Typed[(T1, T2, T3)] = tpd(f1, f2, f3)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3)))
  def typed[T1, T2, T3, T4](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3], f4: OCT[T4]): OrcSchema.Typed[(T1, T2, T3, T4)] = tpd(f1, f2, f3, f4)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3), f4.tpe.valueOf(r._4)))
  def typed[T1, T2, T3, T4, T5](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3], f4: OCT[T4], f5: OCT[T5]): OrcSchema.Typed[(T1, T2, T3, T4, T5)] = tpd(f1, f2, f3, f4, f5)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3), f4.tpe.valueOf(r._4), f5.tpe.valueOf(r._5)))
  def typed[T1, T2, T3, T4, T5, T6](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3], f4: OCT[T4], f5: OCT[T5], f6: OCT[T6]): OrcSchema.Typed[(T1, T2, T3, T4, T5, T6)] = tpd(f1, f2, f3, f4, f5, f6)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3), f4.tpe.valueOf(r._4), f5.tpe.valueOf(r._5), f6.tpe.valueOf(r._6)))
  def typed[T1, T2, T3, T4, T5, T6, T7](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3], f4: OCT[T4], f5: OCT[T5], f6: OCT[T6], f7: OCT[T7]): OrcSchema.Typed[(T1, T2, T3, T4, T5, T6, T7)] = tpd(f1, f2, f3, f4, f5, f6, f7)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3), f4.tpe.valueOf(r._4), f5.tpe.valueOf(r._5), f6.tpe.valueOf(r._6), f7.tpe.valueOf(r._7)))
  def typed[T1, T2, T3, T4, T5, T6, T7, T8](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3], f4: OCT[T4], f5: OCT[T5], f6: OCT[T6], f7: OCT[T7], f8: OCT[T8]): OrcSchema.Typed[(T1, T2, T3, T4, T5, T6, T7, T8)] = tpd(f1, f2, f3, f4, f5, f6, f7, f8)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3), f4.tpe.valueOf(r._4), f5.tpe.valueOf(r._5), f6.tpe.valueOf(r._6), f7.tpe.valueOf(r._7), f8.tpe.valueOf(r._8)))
  def typed[T1, T2, T3, T4, T5, T6, T7, T8, T9](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3], f4: OCT[T4], f5: OCT[T5], f6: OCT[T6], f7: OCT[T7], f8: OCT[T8], f9: OCT[T9]): OrcSchema.Typed[(T1, T2, T3, T4, T5, T6, T7, T8, T9)] = tpd(f1, f2, f3, f4, f5, f6, f7, f8, f9)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3), f4.tpe.valueOf(r._4), f5.tpe.valueOf(r._5), f6.tpe.valueOf(r._6), f7.tpe.valueOf(r._7), f8.tpe.valueOf(r._8), f9.tpe.valueOf(r._9)))
  def typed[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](f1: OCT[T1], f2: OCT[T2], f3: OCT[T3], f4: OCT[T4], f5: OCT[T5], f6: OCT[T6], f7: OCT[T7], f8: OCT[T8], f9: OCT[T9], f10: OCT[T10]): OrcSchema.Typed[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] = tpd(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10)(r => ArraySeq(f1.tpe.valueOf(r._1), f2.tpe.valueOf(r._2), f3.tpe.valueOf(r._3), f4.tpe.valueOf(r._4), f5.tpe.valueOf(r._5), f6.tpe.valueOf(r._6), f7.tpe.valueOf(r._7), f8.tpe.valueOf(r._8), f9.tpe.valueOf(r._9), f10.tpe.valueOf(r._10)))
  private def tpd[A](cols: OrcColumn*)(f: A => ArraySeq[OrcAny]) = new Typed[A](cols)(using OrcEncoder.orcEncoderOrdered.contramap[A](f))
  // @formatter:on

  def fromOrcTypeDescriptor(topLevelTypeDescriptor: TypeDescription): Either[UnsupportedOrcSchema, OrcSchema.Simple] = {
    import OrcColumn.StringOps
    import cats.syntax.traverse.*

    def parseSimpleType(typeDescription: TypeDescription): Either[UnsupportedOrcSchema, OrcColumn] = typeDescription.getCategory match {
      case Category.BOOLEAN           => Right(typeDescription.getFullFieldName.of[Boolean])
      case Category.INT               => Right(typeDescription.getFullFieldName.of[Int])
      case Category.LONG              => Right(typeDescription.getFullFieldName.of[Long])
      case Category.DOUBLE            => Right(typeDescription.getFullFieldName.of[Double])
      case Category.STRING            => Right(typeDescription.getFullFieldName.of[String])
      case Category.TIMESTAMP         => Right(typeDescription.getFullFieldName.of[OffsetDateTime])
      case Category.TIMESTAMP_INSTANT => Right(typeDescription.getFullFieldName.of[OffsetDateTime])
      case Category.DATE              => Right(typeDescription.getFullFieldName.of[OffsetDateTime])
      case Category.BINARY            => Right(typeDescription.getFullFieldName.of[Array[Byte]])
      case Category.LIST              => parseListType(typeDescription)
      case other                      => Left(UnsupportedOrcSchema(s"Expected simple field but got: $other. Nested generic fields are not supported."))
    }

    def parseListType(typeDescription: TypeDescription): Either[UnsupportedOrcSchema, OrcColumn] = {
      val children = typeDescription.getChildren
      if (children != null && children.size() == 1 && children.get(0) != null) {
        children.get(0).getCategory match {
          case Category.BOOLEAN           => Right(typeDescription.getFullFieldName.of[List[Boolean]])
          case Category.INT               => Right(typeDescription.getFullFieldName.of[List[Int]])
          case Category.LONG              => Right(typeDescription.getFullFieldName.of[List[Long]])
          case Category.DOUBLE            => Right(typeDescription.getFullFieldName.of[List[Double]])
          case Category.STRING            => Right(typeDescription.getFullFieldName.of[List[String]])
          case Category.TIMESTAMP         => Right(typeDescription.getFullFieldName.of[List[OffsetDateTime]])
          case Category.TIMESTAMP_INSTANT => Right(typeDescription.getFullFieldName.of[List[OffsetDateTime]])
          case Category.DATE              => Right(typeDescription.getFullFieldName.of[List[OffsetDateTime]])
          case Category.BINARY            => Right(typeDescription.getFullFieldName.of[List[Array[Byte]]])
          case other => Left(UnsupportedOrcSchema(s"Expected simple field as a List subtype but got: $other. Nested generic fields are not supported."))
        }
      } else {
        Left(UnsupportedOrcSchema(s"Orc List have unknown type description."))
      }
    }

    def parseStructType(typeDescription: TypeDescription): Either[UnsupportedOrcSchema, List[OrcColumn]] = {
      val children = typeDescription.getChildren
      if (children != null && !children.isEmpty) {
        typeDescription.getChildren.asScala.toList.traverse(parseSimpleType)
      } else {
        Left(UnsupportedOrcSchema(s"Cannot extract schema from empty struct: ${typeDescription.getCategory}, in ${topLevelTypeDescriptor.toJson}"))
      }
    }

    topLevelTypeDescriptor.getCategory match {
      case Category.STRUCT => parseStructType(topLevelTypeDescriptor).map(OrcSchema.apply)
      case _               => parseSimpleType(topLevelTypeDescriptor).map(c => OrcSchema.from(c))
    }
  }

  final class Typed[Row](columns0: Seq[OrcColumn])(implicit encoder0: OrcEncoder[Row]) extends Simple(columns0) {
    val encoder: OrcEncoder[Row] = encoder0

    // inefficient, use only for testing
    def testEncode(row: Row): ArraySeq[OrcAny] = {
      val tmpBatch = typeDescription.createRowBatch(1)
      encoder.writeColumns(this, tmpBatch, 0, row)
      columns.zip(tmpBatch.cols).map { case (c, col) => c.tpe.read(col, 0) }
    }
  }

  open class Simple(columns0: Seq[OrcColumn]) extends OrcSchema {
    final val columns: ArraySeq[OrcColumn] = ArraySeq.from(columns0)

    final val columnsByNameUnordered: collection.Map[String, OrcColumn] = mutable.HashMap.from(columns.iterator.map(c => c.name -> c))
    final val columnsByIndexUnordered: collection.Map[Int, OrcColumn]   = mutable.HashMap.from(columns.iterator.zipWithIndex.map(_.swap))

    final def typeDescription: TypeDescription = {
      columns.foldLeft(TypeDescription.createStruct()) {
        case (sc, column) => sc.addField(column.name, column.tpe.typeDescription)
      }
    }

    final def indexColumnsFor(desc: TypeDescription): Either[OrcException.SchemaException, ArraySeq[(OrcColumn, Int)]] = {
      import cats.syntax.traverse.*
      val nameToIdx = desc.getFieldNames.asScala.iterator.zipWithIndex.toMap
      columns.traverse {
        column =>
          nameToIdx.get(column.name).map(column -> _).toRight(OrcException.SchemaException(s"schema missing field `${column.name}`"))
      }
    }
  }

  final case class UnsupportedOrcSchema(message: String) extends RuntimeException(message)

}
