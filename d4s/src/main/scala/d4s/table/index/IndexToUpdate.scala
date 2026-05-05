package d4s.table.index

final case class IndexToUpdate(
  create: Set[ProvisionedGlobalIndex[?, ?]],
  modify: Set[GlobalIndexUpdate],
  delete: Set[String],
)
