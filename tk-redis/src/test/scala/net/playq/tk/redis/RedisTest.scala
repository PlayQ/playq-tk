package net.playq.tk.redis

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Clock1, F, IO2}
import izumi.fundamentals.platform.functional.Identity
import net.playq.tk.redis.RedisTestBase.Ctx
import net.playq.tk.redis.aws.ElastiCacheIamCredentialsProvider
import net.playq.tk.redis.config.RedisConfig.RedisAuth
import net.playq.tk.redis.test.RedisTestEnv
import net.playq.tk.test.TkTestBaseCtx
import net.playq.tk.test.rnd.TkRnd.FRandom
import org.redisson.api.options.KeysScanOptions
import zio.IO

import java.net.InetSocketAddress
import java.util.UUID
import scala.jdk.CollectionConverters.IteratorHasAsScala

final class RedisTest extends RedisTestBase[IO] with RedisTestEnv[IO]

abstract class RedisTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {

  "Redis component" should {
    "support requests with auth" in bound {
      ctx =>
        import ctx.*

        for {
          _ <- redisComponent.rawRequest("set-test-value") {
            client => F.syncThrowable(client.getMap[String, String]("test").put("test-key", "test-value"))
          }
          get <- redisComponent.rawRequest("get-test-value") {
            client => F.syncThrowable(client.getMap[String, String]("test").get("test-key"))
          }
          _ <- assertIO(get == "test-value")

          res <- redisComponent.rawRequest("bf-reserve") {
            client =>
              F.syncThrowable {
                val bf = client.getBloomFilter[String]("test-bf-key-123123")
                bf.tryInit(10000, 0.03)
                bf.add("123")
                bf.contains("123")
              }
          }
          _ <- assertIO(res)
        } yield ()
    }

    "make signed request" in bound {
      ctx =>
        import ctx.*
        System.setProperty("aws.accessKeyId", System.getProperty("aws.accessKeyId", "dummy"))
        System.setProperty("aws.secretAccessKey", System.getProperty("aws.secretAccessKey", "dummy"))
        F.sync {
          val auth               = RedisAuth.AwsIAM("user", "rep", "us-east-1")
          val redisCredsProvider = ElastiCacheIamCredentialsProvider(auth, clock)
          val creds              = redisCredsProvider
            .resolve(InetSocketAddress.createUnresolved("localhost", 6363))
            .toCompletableFuture
            .get()
          println(creds.getPassword)
        }
    }

    "get and delete multiple keys" in bound {
      ctx =>
        import ctx.*
        for {
          prefix <- F.random[UUID]
          _      <- redisComponent.rawRequest("set-test-value") {
            client =>
              F.traverse(1 to 1000) {
                _ => F.sync(client.getMap(s"$prefix-${UUID.randomUUID()}").put("test-key", "test-value"))
              }
          }
          getKeys = redisComponent.rawRequest("get-keys") {
            client =>
              F.sync {
                val keyScanOptions = KeysScanOptions.defaults().pattern(s"$prefix-*")
                client.getKeys.getKeys(keyScanOptions).iterator().asScala.toList
              }
          }
          keys1 <- getKeys
          _     <- assertIO(keys1.size == 1000)
          _     <- redisComponent.rawRequest("delete-keys")(client => F.sync(client.getKeys.delete(keys1*)))
          keys2 <- getKeys
          _     <- assertIO(keys2.isEmpty)
        } yield ()
    }
  }

  // cluster test:
//  override def moduleOverrides: distage.Module = super.moduleOverrides ++ new distage.ModuleDef {
//    import net.playq.tk.redis.config.RedisConfig
//    make[RedisConfig].from(
//      RedisConfig(
//        endpoint = "redis://127.0.0.1:23000,redis://127.0.0.1:23001,redis://127.0.0.1:23002,redis://127.0.0.1:23003,redis://127.0.0.1:23004,redis://127.0.0.1:23005",
//        integrationCheck      = true,
//        serverMode            = RedisServerMode.ClusterMode,
//        user                  = None,
//        password              = None,
//        authMode              = RedisAuthMode.NoAuthMode,
//        awsUserId             = None,
//        awsReplicationGroupId = None,
//        awsRegion             = None,
//      )
//    )
//  }
}

object RedisTestBase {
  final case class Ctx[F[+_, +_]](redisComponent: RedisComponent[F], clock: Clock1[Identity])
}
