package net.playq.tk.aws.s3.models

/** @param fileName S3 path excluding bucket name (aka `key`) */
final case class S3File(fileName: String, format: S3FileFormat)
