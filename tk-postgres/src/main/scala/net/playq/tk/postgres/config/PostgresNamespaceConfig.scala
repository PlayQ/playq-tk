package net.playq.tk.postgres.config
import net.playq.tk.postgres.partitioning.model.TableName

final case class PostgresNamespaceConfig(
  namespaceSchema: String
) {
  infix def table(table: String): TableName = {
    TableName(namespaceSchema, table)
  }
}
