package net.playq.tk.aws.s3

import izumi.distage.model.definition.{Lifecycle, Lifecycle2}
import izumi.functional.bio.{Async2, F, IO2}
import logstage.LogIO2
import net.playq.tk.aws.s3.config.S3Config
import net.playq.tk.aws.s3.models.*
import net.playq.tk.quantified.AsyncThrowable
import software.amazon.awssdk.services.s3.model.*

import scala.collection.concurrent.TrieMap

trait S3BucketFactory[F[+_, +_]] {
  def mkClient[BucketId <: S3BucketId](bucketId: BucketId): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]]
  def wrapClient[BucketId <: S3BucketId](bucketId: BucketId)(component: S3Component[F]): S3Bucket[F, BucketId]
}

object S3BucketFactory {

  final class Dummy[F[+_, +_]: IO2] extends S3BucketFactory[F] {
    private val buckets = TrieMap.empty[String, S3Bucket[F, ?]]

    override def mkClient[BucketId <: S3BucketId](bucketId: BucketId): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]] = {
      Lifecycle.liftF(F.sync(getOrCreateDummyBucket(bucketId)))
    }
    override def wrapClient[BucketId <: S3BucketId](bucketId: BucketId)(component: S3Component[F]): S3Bucket[F, BucketId] = {
      getOrCreateDummyBucket(bucketId)
    }
    private def getOrCreateDummyBucket[BucketId <: S3BucketId](bucketId: BucketId): S3Bucket[F, BucketId] = {
      buckets.getOrElseUpdate(bucketId.bucketName, new S3Bucket.Dummy[F, BucketId](bucketId)).asInstanceOf[S3Bucket[F, BucketId]]
    }
  }

  final class Impl[F[+_, +_]: Async2: AsyncThrowable](
    generalClient: S3Component[F],
    clientFactory: S3ComponentFactory[F],
    config: S3Config,
  )(implicit
    log: LogIO2[F]
  ) extends S3BucketFactory[F] {
    import S3Bucket.ThrowableS3Ops

    override def mkClient[BucketId <: S3BucketId](bucketId: BucketId): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]] = {
      (bucketId, config.getEndpoint) match {
        // Managed Bucket:
        // trying to locate bucket and make client, if not found - create a new bucket in the default service region
        // when client is ready will check its tags and CORS policy and update if needed
        case (id: S3BucketId.Managed, _) if id.serviceCreatesBucket =>
          locateOrCreateBucketAndMakeClient(bucketId).evalTap(updateBucketTagsAndPolicy(id))

        // Provided Bucket:
        // trying to locate bucket and make client, failing if the bucket is not exists
        case (_, None) =>
          locateBucketAndMakeClient(bucketId).evalMap {
            case Some(bucket) => F.pure(bucket)
            case None         => F.fail(new RuntimeException(s"Can not find provided ${bucketId.bucketName}."))
          }

        // Provided Bucket in local env:
        // trying to locate bucket and make client, if not found - creates a new bucket
        case (_, Some(localEnv)) =>
          Lifecycle.suspend {
            log
              .info(s"Trying to locate provided $bucketId in $localEnv, with create on fallback.")
              .as(locateOrCreateBucketAndMakeClient(bucketId))
          }
      }
    }

    override def wrapClient[BucketId <: S3BucketId](bucketId: BucketId)(component: S3Component[F]): S3Bucket[F, BucketId] = {
      new S3Bucket.Impl(bucketId, component)
    }

    private def locateOrCreateBucketAndMakeClient[BucketId <: S3BucketId](
      bucketId: BucketId
    ): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]] = {
      locateBucketAndMakeClient(bucketId).flatMap {
        case Some(bucket) =>
          Lifecycle.pure[F[Throwable, _]](bucket)
        case None =>
          Lifecycle.suspend {
            log
              .info(s"Failed to locate ${bucketId.bucketName}, creating new one.")
              .as(createBucketAndMakeClient(bucketId))
          }
      }
    }

    private def locateBucketAndMakeClient[BucketId <: S3BucketId](
      bucketId: BucketId
    ): Lifecycle2[F, Throwable, Option[S3Bucket[F, BucketId]]] = Lifecycle.suspend {
      for {
        _ <- bucketId match {
          case bucketId: S3BucketId.Managed => log.info(s"Locating managed ${bucketId.name -> "bucket"} with ${bucketId.bucketName}.")
          case _                            => log.info(s"Locating provided ${bucketId.bucketName}.")
        }
        maybeRegion <- generalClient
          .getBucketRegion(bucketId.bucketName).redeem(
            {
              case _: NoSuchBucketException => F.pure(None)
              case err                      => F.fail(err)
            },
            region => log.info(s"Located ${bucketId.bucketName} in $region.").as(Some(region)),
          ).logThrowable("GetBucketLocation", bucketId.bucketName)
      } yield {
        maybeRegion match {
          case Some(region) => clientFactory.mkComponent(Some(region), None, bucketId.retryConfig).map(wrapClient(bucketId)).map(Some.apply)
          case None         => Lifecycle.pure[F[Throwable, _]](None)
        }
      }
    }

    private def createBucketAndMakeClient[BucketId <: S3BucketId](
      bucketId: BucketId
    ): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]] = Lifecycle.suspend {
      for {
        _ <- bucketId match {
          case bucketId: S3BucketId.Managed => log.info(s"Creating managed ${bucketId.name -> "bucket"} with ${bucketId.bucketName}.")
          case _                            => log.info(s"Creating provided ${bucketId.bucketName}.")
        }
        _ <- generalClient
          .rawRequest("create-bucket")(_.createBucket(CreateBucketRequest.builder().bucket(bucketId.bucketName).build()))
          .catchSome { case _: BucketAlreadyExistsException => F.unit }
          .logThrowable("CreateBucket", bucketId.bucketName)
      } yield {
        clientFactory.mkComponent(None, None, bucketId.retryConfig).map(wrapClient(bucketId))
      }
    }

    private def updateBucketTagsAndPolicy[BucketId <: S3BucketId](
      id: S3BucketId.Managed
    )(bucket: S3Bucket[F, BucketId]
    ): F[Throwable, Unit] = {
      for {
        _ <- F.when(id.tags.nonEmpty) {
          bucket.getBucketTags.flatMap {
            oldTags =>
              F.when(id.tags.toSet.diff(oldTags.toSet).nonEmpty) {
                val mergedTags = oldTags ++ id.tags
                for {
                  _ <- log.info {
                    s"""Old ${bucket.bucketName} tags does not correspond to a generated one. Will update.
                       |${oldTags -> "oldTags"}
                       |${mergedTags -> "newTags"}""".stripMargin
                  }
                  _ <- bucket.setBucketTags(mergedTags.map(S3Tag.toTag).toSeq)
                } yield ()
              }
          }
        }
        _ <- F.when(id.cors.nonEmpty) {
          bucket.getBucketCors.flatMap {
            oldCors =>
              F.when(id.cors.diff(oldCors).nonEmpty) {
                for {
                  _ <- log.info {
                    s"""Old ${bucket.bucketName} CORS does not correspond to a generated one. Will update.
                       |${oldCors -> "oldCors"}
                       |${id.cors -> "newCors"}""".stripMargin
                  }
                  _ <- bucket.setBucketCors(id.cors)
                } yield ()
              }
          }.catchAll {
            err =>
              log.error(s"Failed to update ${bucket.bucketName} CORS configuration. Ignoring. $err")
          }
        }
      } yield ()
    }
  }

}
