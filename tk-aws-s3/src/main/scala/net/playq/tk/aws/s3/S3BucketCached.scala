package net.playq.tk.aws.s3

import izumi.distage.model.definition.Lifecycle
import net.playq.tk.util.ClosableFile

trait S3BucketCached[F[_, _], BucketId <: S3BucketId] {
  def get(key: String): Lifecycle[F[Throwable, _], ClosableFile]
  private[s3] def acquire(key: String): F[Throwable, ClosableFile]
  private[s3] def release(key: String): F[Nothing, Unit]
  def evict(key: String): F[Nothing, Unit]
  def reset(): F[Nothing, Unit]
}

object S3BucketCached {
  final class FromS3Cache[F[_, _], BucketId <: S3BucketId](
    bucket: S3Bucket[F, BucketId],
    cache: S3Cache[F],
  ) extends S3BucketCached[F, BucketId] {
    override def get(key: String): Lifecycle[F[Throwable, _], ClosableFile]   = cache.get(bucket, key)
    override private[s3] def acquire(key: String): F[Throwable, ClosableFile] = cache.acquire(bucket, key)
    override private[s3] def release(key: String): F[Nothing, Unit]           = cache.release(bucket, key)
    override def evict(key: String): F[Nothing, Unit]                         = cache.evict(bucket, key)
    override def reset(): F[Nothing, Unit]                                    = cache.cleanUnused()
  }
}
