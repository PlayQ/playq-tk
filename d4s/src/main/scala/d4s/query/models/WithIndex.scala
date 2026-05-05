package d4s.query.models

import d4s.table.index.TableIndex

trait WithIndex[+D] {
  def withIndex(index: TableIndex[?, ?]): D
}
