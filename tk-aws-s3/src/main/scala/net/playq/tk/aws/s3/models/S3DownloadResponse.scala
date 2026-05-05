package net.playq.tk.aws.s3.models

import java.time.Instant

final case class S3DownloadResponse(content: Array[Byte], contentType: String, modifiedAt: Instant)
