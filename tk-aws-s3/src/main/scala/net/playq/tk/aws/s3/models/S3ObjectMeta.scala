package net.playq.tk.aws.s3.models

final case class S3ObjectMeta(
  bucketName: String,
  key: String,
  eTag: String,
  size: Long,
  modifiedAt: Long,
)
