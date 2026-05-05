package d4s.query.models

import d4s.query.DynamoQuery
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity

trait WithResponseConsumed[DQ[+_], +Dec] extends DynamoQuery[DQ, Dec] {
  self: DQ[Dec] =>

  def extractConsumedCapacity(rsp: Rsp): ConsumedCapacity

  final def consumedCapacityOnly: DQ[ConsumedCapacity]        = decode(extractConsumedCapacity)
  final def withConsumedCapacity: DQ[(Dec, ConsumedCapacity)] = decodeWith((a, c) => (c, extractConsumedCapacity(a)))
}
