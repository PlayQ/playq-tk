package net.playq.tk.aws.s3

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.distage.testkit.model.TestConfig
import izumi.functional.bio.catz.*
import izumi.functional.bio.{BlockingIO2, Clock2, F, IO2, Parallel2, Primitives2}
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.s3.S3BaseTest.{Ctx, TestBucketId}
import net.playq.tk.aws.s3.config.S3Config.S3BucketsConfig
import net.playq.tk.aws.s3.models.*
import net.playq.tk.aws.s3.models.S3UploadContent.Bytes
import net.playq.tk.aws.s3.test.S3TestEnv
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.test.rnd.TkRndBase
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import zio.IO

import java.io.File
import java.nio.file.Files
import java.util.UUID
import scala.annotation.unused

final class S3BaseTest extends S3BaseTestBase[IO] with WithProduction

object S3BaseTest {
  final class TestBucketId(implicit s3BucketsConfig: S3BucketsConfig, nameSpace: AwsNameSpace) extends S3BucketId.Managed("test-bucket", ServiceName("test"))
  final case class Ctx[F[+_, +_]](
    bucket: S3Bucket[F, TestBucketId],
    bucketCached: S3BucketCached[F, TestBucketId],
  )(implicit
    val blockingIO2: BlockingIO2[F],
    val clock2: Clock2[F],
    val primitives2: Primitives2[F],
    val parallel2: Parallel2[F],
  )
}

abstract class S3BaseTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with S3TestEnv[F] with TkRndBase {

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    S3Bucket.makeBucketCached[F, TestBucketId]
  }

  override protected def parallelTestExecution: TestConfig.Parallelism = TestConfig.Parallelism.Sequential

  "s3 lib" should {
    "S3record.tagged preserves type" in {
      @unused def x: S3Record[Bytes] = null
      @unused def y: S3Record[Bytes] = x.tagged("abc")
    }

    "perform base operations" in bound {
      ctx =>
        import ctx.*
        for {
          appId   <- F.random[UUID]
          testPath = "test-path/x/test"
          record   = S3Record(S3File(testPath, S3FileFormat.Unknown), S3UploadContent.String("test")).tagged(appId)

          _    <- bucket.healthProbe
          _    <- bucket.upload(record)
          cntn <- bucket.download(testPath)
          _    <- assertIO(cntn.exists(c => new String(c.content) == "test"))
          _    <- bucket.downloadToFile(testPath).evalMap(F.fromOption(new RuntimeException("Missing file."))(_)).use {
            file => assertIO(new String(Files.readAllBytes(file.absolutePath)) == "test")
          }
          tags1 <- bucket.getObjectTags(testPath)
          _     <- assertIO(tags1.size == 1)
          _     <- assertIO(tags1.exists(_ == S3Tag[UUID].asTuple(appId)))
          _     <- bucket.tagObject(testPath, "test_tag" -> "test")
          tags2 <- bucket.getObjectTags(testPath)
          _     <- assertIO(tags2.size == 2)
          meta  <- bucket.meta(testPath).fromOption(new RuntimeException("Missing key."))
          _     <- assertIO(meta.size == record.content.string.getBytes.length)
          _     <- bucket.doesObjectExists(testPath).flatMap(assertIO(_))
          _     <- bucket.delete(testPath)
          _     <- bucket.doesObjectExists(testPath).flatMap(b => assertIO(!b))
          _     <- bucket.putObjectPresigned(testPath, None, Some("test"), List(S3Tag.toTag(UUID.randomUUID())))
          _     <- bucket.upload(record)
          _     <- bucket.getObjectPresigned(testPath, responseContentType = Some("text"), responseContentDisposition = None, None)
          _     <- bucket.getObjectPresigned(testPath, responseContentType = Some("text"), responseContentDisposition = Some("text.txt"), None)
          _     <- bucket.streamObjects("test-path").compile.toList.flatMap(r => assertIO(r.size == 1))
          _     <- bucket.listFolders("test-path/").flatMap(r => assertIO(r == List("test-path/x/")))
          files <- F.traverse(1 to 2000) {
            i =>
              val path   = s"test/batch/$i"
              val record = S3Record(S3File(path, S3FileFormat.Unknown), S3UploadContent.String("test"))
              bucket.upload(record).as(path)
          }
          _   <- bucket.deleteBatch(files)
          rsp <- bucket.download(files.head)
          _   <- assertIO(rsp.isEmpty)
        } yield ()
    }

    "use cached files" in bound {
      ctx =>
        import ctx.*

        for {
          appId    <- F.random[UUID]
          testPath <- F.random[UUID].map(u => s"cached-test/$u")
          record    = S3Record(S3File(testPath, S3FileFormat.Unknown), S3UploadContent.String("test")).tagged(appId)

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

    "use cached files in parallel" in bound {
      ctx =>
        import ctx.*

        for {
          appId    <- F.random[UUID]
          testPath <- F.random[UUID].map(u => s"cached-test/$u")
          record    = S3Record(S3File(testPath, S3FileFormat.Unknown), S3UploadContent.String("test")).tagged(appId)

          _   <- bucket.upload(record)
          ref <- F.mkRef(Option.empty[File])
          _   <- F.parTraverseN(5)((1 to 5).toList) {
            _ =>
              for {
                file    <- bucketCached.acquire(testPath)
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

    "stream all the directories and files in a very large folder" in bound {
      ctx =>
        import ctx.*

        for {
          appId <- F.random[UUID]

          _ <- F.parTraverseN_(128)(1 to 2048) {
            n =>
              F.traverse_(Seq(1, 2, 3)) {
                k =>
                  bucket.upload(S3Record(S3File(s"directory-test-$appId/dir-$n/file-$n-$k.json", S3FileFormat.JSON), S3UploadContent.String("{}")))
              }
          }
          countFolders <- bucket.streamFolders(s"directory-test-$appId/").compile.count
          _            <- assertIO(countFolders == 2048)
          countFiles   <- bucket.streamObjects(s"directory-test-$appId/").compile.count
          _            <- assertIO(countFiles == 6144)
        } yield ()
    }
  }

}
