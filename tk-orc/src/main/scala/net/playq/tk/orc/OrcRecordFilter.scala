package net.playq.tk.orc

import net.playq.tk.orc.OrcValue.OrcAny
import org.apache.orc.OrcFilterContext

import java.util.function.Consumer

trait OrcRecordFilter {
  val field: OrcColumn
  def filter(value: OrcAny): Boolean
}

object OrcRecordFilter {
  def apply(f: OrcColumn)(predicate: OrcAny => Boolean): OrcRecordFilter = new OrcRecordFilter {
    override val field: OrcColumn               = f
    override def filter(value: OrcAny): Boolean = predicate(value)
  }

  def createFilter(filters: List[OrcRecordFilter]): Consumer[OrcFilterContext] = new Consumer[OrcFilterContext] {
    override def accept(t: OrcFilterContext): Unit = {
      val preparedFilters: List[Int => Boolean] = filters.map {
        filter =>
          val vector = t.findColumnVector(filter.field.name)(0)
          (idx: Int) => filter.filter(filter.field.tpe.read(vector, idx))
      }
      val combinedFilter: Int => Boolean = (idx: Int) => preparedFilters.forall(_.apply(idx))
      // orc for some reason requires direct array update
      // using `t.setSelected(<selected>)` is unsafe and might broke whole reader
      val selectedArray = t.updateSelected(t.getSelectedSize)
      val selectedSize  = Iterator.range(0, t.getSelectedSize).foldLeft(0) {
        case (selectedIdx, idx) if combinedFilter(idx) =>
          selectedArray(selectedIdx) = idx
          selectedIdx + 1
        case (selectedIdx, _) =>
          selectedIdx
      }
      t.setSelectedSize(selectedSize)
      t.setSelectedInUse(true)
    }
  }
}
