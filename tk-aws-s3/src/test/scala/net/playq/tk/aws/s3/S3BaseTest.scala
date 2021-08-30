package net.playq.tk.aws.s3

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.distage.testkit.TestConfig
import izumi.functional.bio.catz.*
import izumi.functional.bio.{F, IO2, Parallel2, Primitives2}
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.s3.S3BaseTest.{Ctx, TestBucketId}
import net.playq.tk.aws.s3.config.S3Config
import net.playq.tk.aws.s3.models.S3UploadContent.Bytes
import net.playq.tk.aws.s3.models.*
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import zio.IO

import java.io.File
import java.util.UUID
import scala.annotation.unused

final class S3BaseTest extends S3BaseTestBase[IO] with WithProduction

object S3BaseTest {
  final class TestBucketId(implicit s3Config: S3Config, nameSpace: AwsNameSpace) extends S3BucketId.GenWithOverride("test-bucket", ServiceName("test"))
  final case class Ctx[F[+_, +_]](
    bucket: S3Bucket[F, TestBucketId],
    bucketCached: S3BucketCached[F, TestBucketId],
  )
}

abstract class S3BaseTestBase[F[+_, +_]: IO2: Primitives2: Parallel2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    include(S3Bucket.bucketCachedModule[F, TestBucketId])
  }

  "S3record.withTag preserves type" in {
    def x: S3Record[Bytes]         = null
    @unused def y: S3Record[Bytes] = x.tagged("abc")
  }

  "perform base operations" in scopeIO {
    ctx =>
      import ctx.*
      val appId    = UUID.randomUUID()
      val testPath = "test-path/test"
      val record   = S3Record(S3File(testPath, S3FileFormat.Unknown), S3UploadContent.String("test")).tagged(appId)
      for {
        _     <- bucket.upload(record)
        tags1 <- bucket.getObjectTags(testPath)
        _     <- assertIO(tags1.size == 1)
        _     <- assertIO(tags1.exists(_ == S3Tag[UUID].asTuple(appId)))
        _     <- bucket.tagObject(testPath, "test_tag" -> "test")
        tags2 <- bucket.getObjectTags(testPath)
        _     <- assertIO(tags2.size == 2)
        _     <- bucket.doesObjectExists(testPath).flatMap(assertIO(_))
        _     <- bucket.delete(testPath)
        _     <- bucket.doesObjectExists(testPath).flatMap(b => assertIO(!b))
        _     <- bucket.upload(record)
        _     <- bucket.getObjectPresigned(testPath, responseContentType = Some("text"), responseContentDisposition = None)
        _     <- bucket.getObjectPresigned(testPath, responseContentType = Some("text"), responseContentDisposition = Some("text.txt"))
        _     <- bucket.streamObjects("test-path").compile.toList.flatMap(r => assertIO(r.size == 1))
      } yield ()
  }

  "use cached files" in scopeIO {
    ctx =>
      import ctx.*
      val appId    = UUID.randomUUID()
      val testPath = s"cached-test/${UUID.randomUUID()}"
      val record   = S3Record(S3File(testPath, S3FileFormat.Unknown), S3UploadContent.String("test")).tagged(appId)
      for {
        _ <- bucket.upload(record)

        file1 <- bucketCached.acquire(testPath)
        file2 <- bucketCached.acquire(testPath)
        _     <- assertIO(file1.file == file2.file)

        _     <- bucketCached.evict(testPath)
        file3 <- bucketCached.acquire(testPath)
        _     <- assertIO(file1.file == file3.file)

        _     <- bucketCached.reset()
        file4 <- bucketCached.acquire(testPath)
        _     <- assertIO(file1.file == file4.file)

        _ <- bucketCached.release(testPath)
        _ <- bucketCached.release(testPath)
        _ <- bucketCached.release(testPath)
        _ <- bucketCached.release(testPath)

        _     <- bucketCached.reset()
        file5 <- bucketCached.acquire(testPath)
        _     <- assertIO(file1.file != file5.file)
        _     <- bucketCached.release(testPath)

        _ <- bucketCached.reset()
      } yield ()
  }

  "use cached files in parallel" in scopeIO {
    ctx =>
      import ctx.*
      val appId    = UUID.randomUUID()
      val testPath = s"cached-test/${UUID.randomUUID()}"
      val record   = S3Record(S3File(testPath, S3FileFormat.Unknown), S3UploadContent.String("test")).tagged(appId)
      for {
        _   <- bucket.upload(record)
        ref <- F.mkRef(Option.empty[File])
        _ <- F.parTraverseN(5)((1 to 5).toList) {
          _ =>
            for {
              file <- bucketCached.acquire(testPath)
              theSame <- ref.modify {
                case Some(old) => (old == file.file) -> Some(old)
                case None      => true               -> Some(file.file)
              }
              _ <- assertIO(theSame)
              _ <- bucketCached.release(testPath)
            } yield ()
        }
        _ <- bucketCached.reset()
      } yield ()
  }

  override protected def parallelTestExecution: TestConfig.ParallelLevel = TestConfig.ParallelLevel.Sequential
}
