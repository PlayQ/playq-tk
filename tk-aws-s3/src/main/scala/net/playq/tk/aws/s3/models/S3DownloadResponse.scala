package net.playq.tk.aws.s3.models

final case class S3DownloadResponse(content: Array[Byte], contentType: String)
