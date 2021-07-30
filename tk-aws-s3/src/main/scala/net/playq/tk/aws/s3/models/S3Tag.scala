package net.playq.tk.aws.s3.models

import software.amazon.awssdk.services.s3.model.Tag

import java.util.UUID

trait S3Tag[T] {
  def asTuple(t: T): (String, String)

  final def asTag(t: T): Tag = {
    val (k, v) = asTuple(t)
    Tag.builder().key(k).value(v).build()
  }
}

object S3Tag {
  def apply[T: S3Tag]: S3Tag[T] = implicitly

  def toTag[T: S3Tag](t: T): Tag = S3Tag[T].asTag(t)

  implicit val tupleS3Tag: S3Tag[(String, String)] = identity
  implicit val stringIdS3Tag: S3Tag[String]        = ("str_tag", _)
  implicit val awsTagS3Tag: S3Tag[Tag]             = t => (t.key, t.value)
  implicit val uuidS3Tag: S3Tag[UUID]              = t => ("uuid_tag", t.toString)
}
