package net.playq.tk.orc

import net.playq.tk.orc.OrcType.OrcTypeLike

@SuppressWarnings(Array("FinalModifierOnCaseClass"))
sealed case class OrcColumn(name: String, tpe: OrcType)

object OrcColumn {
  implicit final class StringOps(private val name: String) extends AnyVal {
    @inline def of[T: OrcTypeLike]: OrcColumn.Typed[T] = new OrcColumn.Typed(name, OrcType[T])
  }

  /** for [[OrcSchema.typed]] */
  final class Typed[T](override val name: String, override val tpe: OrcType.OrcTypeLike[T]) extends OrcColumn(name, tpe)
}
