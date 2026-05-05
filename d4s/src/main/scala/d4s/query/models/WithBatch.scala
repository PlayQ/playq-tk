package d4s.query.models

import d4s.codecs.D4SEncoder
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

trait WithBatch[+D] {
  def batchItems: List[Map[String, AttributeValue]]

  /** Overwrites batch list, subsequent calls do not append. */
  def withBatch(batchItems: List[Map[String, AttributeValue]]): D

  /** Overwrites batch list, subsequent calls do not append. */
  @inline final def withBatch[Item: D4SEncoder.AsObject](batchItems: Iterable[Item]): D = {
    withBatch(batchItems.map(D4SEncoder.AsObject[Item].encodeObject).toList)
  }
}
