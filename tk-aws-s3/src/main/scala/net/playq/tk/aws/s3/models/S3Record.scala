package net.playq.tk.aws.s3.models

final case class S3Record[+T](
  file: S3File,
  content: T,
  cacheControl: Option[String] = None,
) {
  def tagged[Tag, A](tag: Tag)(implicit ev1: S3Tag[Tag], ev2: T <:< S3UploadContent.Aux[A]): S3Record[A] = {
    copy(content = content.tagged(tag))
  }
}
