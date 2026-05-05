package d4s.config

final case class DynamoTransactionConfig(
  writeTransactSize: Int,
  getTransactSize: Int,
)
