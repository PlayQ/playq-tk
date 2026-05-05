package d4s.query.models

import d4s.UnsafeDynamoOps

trait WithPagesSize[+D] {
  /** Warning: The same limit must be passed to `readPaged` if used with paging.
    * `withPageSize` is redundant if used with `readOffset`.
    * `withPageSize` does not work intuitively with `readStream` - limit applies to a single page, not the stream.
    *
    * import `d4s.unsafe.unsafeDynamoOps` to acknowledge and use anyway.
    */
  def withPageSize(l: Int)(implicit unsafe: UnsafeDynamoOps): D
}
