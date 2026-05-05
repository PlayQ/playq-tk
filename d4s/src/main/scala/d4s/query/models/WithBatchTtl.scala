package d4s.query.models

import d4s.codecs.D4SEncoder

import java.time.ZonedDateTime

trait WithBatchTtl[+D] extends WithBatch[D] with WithTableReference.Generic[D] {
  self: D =>

  /** Overwrites batch list, subsequent calls do not append. */
  @inline final def withBatch[Item: D4SEncoder.AsObject](batchItems: Iterable[Item], ttl: Long): D = {
    tableRef.ttlField match {
      case Some(ttlName) => withBatch(batchItems.map(ItemWithTtl.encodeWithTtl(ttlName, Some(ttl))))
      case None          => withBatch(batchItems.map(i => D4SEncoder.AsObject[Item].encodeObject(i)))
    }
  }

  /** Overwrites batch list, subsequent calls do not append. */
  @inline final def withBatch[Item: D4SEncoder.AsObject](batchItems: Iterable[Item], ttl: ZonedDateTime): D = {
    withBatch(batchItems, ttl.toEpochSecond)
  }

  /** Overwrites batch list, subsequent calls do not append. */
  @inline final def withBatchTtl[Item: D4SEncoder.AsObject](batchItems: Iterable[ItemWithTtl[Item]]): D = {
    tableRef.ttlField match {
      case Some(ttlName) => withBatch(batchItems.map(_.encode(ttlName)))
      case None          => withBatch(batchItems.map(i => D4SEncoder.AsObject[Item].encodeObject(i.item)))
    }
  }
}
