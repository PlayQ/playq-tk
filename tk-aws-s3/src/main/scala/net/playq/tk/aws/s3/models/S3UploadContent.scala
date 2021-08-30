package net.playq.tk.aws.s3.models

import java.io.InputStream
import java.nio.charset.StandardCharsets
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, Tag, Tagging}

import scala.Predef
import scala.Predef.*
import scala.jdk.CollectionConverters.*

sealed trait S3UploadContent {
  def tags: List[Tag]
  def tagged[T: S3Tag](t: T): S3UploadContent

  final def toRequest(
    bucketName: String,
    filename: String,
  )(tweak: PutObjectRequest.Builder => PutObjectRequest.Builder
  ): (PutObjectRequest, RequestBody) = {
    val (builder0, body) = toRequestBuilder(bucketName, filename)
    val builder = if (tags.nonEmpty) {
      builder0.tagging(Tagging.builder().tagSet(tags.distinctBy(_.key).asJava).build())
    } else builder0
    val request = tweak(builder).build()
    request -> body
  }

  private[this] def toRequestBuilder(bucketName: String, filename: String): (PutObjectRequest.Builder, RequestBody) = this match {
    case S3UploadContent.Bytes(bytes, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename).contentLength(bytes.length.toLong) -> RequestBody.fromBytes(bytes)

    case S3UploadContent.File(file, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename) -> RequestBody.fromFile(file)

    case S3UploadContent.String(string, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename) -> RequestBody.fromString(string, StandardCharsets.UTF_8)

    case S3UploadContent.Stream(stream, contentLength, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename) -> RequestBody.fromInputStream(stream, contentLength)
  }
}

object S3UploadContent {
  type Aux[+A] = S3UploadContent { def tagged[T: S3Tag](t: T): A }

  final case class Bytes(bytes: Array[Byte], tags: List[Tag] = Nil) extends S3UploadContent {
    override def tagged[T: S3Tag](t: T): Bytes = copy(tags = S3Tag.toTag(t) :: tags)
  }

  final case class File(file: java.io.File, tags: List[Tag] = Nil) extends S3UploadContent {
    override def tagged[T: S3Tag](t: T): File = copy(tags = S3Tag.toTag(t) :: tags)
  }

  final case class String(string: Predef.String, tags: List[Tag] = Nil) extends S3UploadContent {
    override def tagged[T: S3Tag](t: T): String = copy(tags = S3Tag.toTag(t) :: tags)
  }

  final case class Stream(stream: InputStream, contentLength: Long, tags: List[Tag] = Nil) extends S3UploadContent {
    override def tagged[T: S3Tag](t: T): Stream = copy(tags = S3Tag.toTag(t) :: tags)
  }
}
