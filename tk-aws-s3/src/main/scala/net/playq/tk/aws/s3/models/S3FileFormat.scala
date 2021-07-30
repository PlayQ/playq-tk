package net.playq.tk.aws.s3.models

sealed class S3FileFormat(
  val extension: String   = "unknown",
  val contentType: String = "application/octet-stream",
)

object S3FileFormat {
  def apply(contentType: String): S3FileFormat = new S3FileFormat(contentType = contentType)

  case object CSV extends S3FileFormat("csv", "text/csv")
  case object ORC extends S3FileFormat("orc", "application/octet-stream")
  case object HDF5 extends S3FileFormat("hdf5", "application/octet-stream")
  case object Parquet extends S3FileFormat("parquet", "application/parquet")
  case object Unknown extends S3FileFormat("unknown", "application/octet-stream")
  case object JSON extends S3FileFormat("json", "application/json")
  case object JSONGZIP extends S3FileFormat("json.gz", "application/x-gzip")
  case object TARGZIP extends S3FileFormat("tar.gz", "application/gzip")
}
