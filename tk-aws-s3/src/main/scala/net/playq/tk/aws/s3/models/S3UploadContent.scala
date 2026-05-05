package net.playq.tk.aws.s3.models

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, Tag, Tagging}

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import scala.jdk.CollectionConverters.*

sealed trait S3UploadContent {
  def tags: List[Tag]
  def tagged[T: S3Tag](t: T): S3UploadContent

  final def toRequest(
    bucketName: String,
    filename: String,
  )(tweak: PutObjectRequest.Builder => PutObjectRequest.Builder
  ): (PutObjectRequest, AsyncRequestBody) = {
    val (builder0, body) = toRequestBuilder(bucketName, filename)
    val builder          = if (tags.nonEmpty) {
      builder0.tagging(Tagging.builder().tagSet(tags.distinctBy(_.key).asJava).build())
    } else builder0
    val request = tweak(builder).build()
    request -> body
  }

  private[this] def toRequestBuilder(bucketName: String, filename: String): (PutObjectRequest.Builder, AsyncRequestBody) = this match {
    case S3UploadContent.Bytes(bytes, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename).contentLength(bytes.length.toLong) -> AsyncRequestBody.fromBytes(bytes)

    case S3UploadContent.File(file, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename) -> AsyncRequestBody.fromFile(file)

    case S3UploadContent.String(string, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename) -> AsyncRequestBody.fromString(string, StandardCharsets.UTF_8)

    case S3UploadContent.Stream(stream, contentLength, executorService, _) =>
      PutObjectRequest.builder.bucket(bucketName).key(filename) -> AsyncRequestBody.fromInputStream(stream, contentLength, executorService)

    case json: S3UploadContent.Json[?] =>
      PutObjectRequest.builder.bucket(bucketName).key(filename) -> AsyncRequestBody.fromString(json.stringBody, StandardCharsets.UTF_8)
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

  final case class Stream(stream: InputStream, contentLength: Long, executorService: ExecutorService, tags: List[Tag] = Nil) extends S3UploadContent {
    override def tagged[T: S3Tag](t: T): Stream = copy(tags = S3Tag.toTag(t) :: tags)
  }

  final case class Json[A: Encoder](body: A, tags: List[Tag] = Nil) extends S3UploadContent {
    override def tagged[T: S3Tag](t: T): Json[A] = copy(tags = S3Tag.toTag(t) :: tags)
    def stringBody: Predef.String                = body.asJson.noSpaces
  }
}
