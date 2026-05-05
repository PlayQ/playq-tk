package net.playq.tk.aws.s3.models

import java.time.Instant

final case class S3ObjectMeta(
  bucketName: String,
  key: String,
  eTag: String,
  size: Long,
  modifiedAt: Instant,
)
