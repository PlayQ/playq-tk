package net.playq.tk.redis

import distage.Id
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.functional.bio.{BlockingIO2, Clock1, F, IO2, Primitives2, RefM2}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.metrics.{MacroMetricRedisMeter, MacroMetricRedisTimer, Metrics}
import net.playq.tk.redis.aws.ElastiCacheIamCredentialsProvider
import net.playq.tk.redis.config.RedisConfig
import net.playq.tk.redis.config.RedisConfig.{RedisAuth, RedisServerMode}
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

import java.net.URI
import scala.jdk.CollectionConverters.*

trait RedisComponent[F[_, _]] {
  def rawRequest[A](
    metric: String
  )(f: RedissonClient => F[Throwable, A]
  )(implicit
    saveCounter: MacroMetricRedisMeter[metric.type],
    saveTimer: MacroMetricRedisTimer[metric.type],
  ): F[Throwable, A]
}

object RedisComponent {

  final case class WrappedRedissonClient[F[+_, +_]: IO2](
    redisConfig: RedisConfig,
    clock: Clock1[Identity],
    ref: RefM2[F, Option[RedissonClient]],
  ) {
    private def createClient: F[Throwable, RedissonClient] = F.syncThrowable {
      val config = new Config()

      redisConfig.auth match {
        case RedisAuth.NoAuth                       => ()
        case RedisAuth.UserPassword(user, password) => config.setPassword(password).setUsername(user)
        case iam: RedisAuth.AwsIAM                  => config.setCredentialsResolver(ElastiCacheIamCredentialsProvider(iam, clock))
      }

      redisConfig.serverMode match {
        case RedisServerMode.SingleMode     => config.useSingleServer().setAddress(redisConfig.endpoints.head)
        case RedisServerMode.ClusterMode    => config.useClusterServers().setNodeAddresses(redisConfig.endpoints.toList.asJava)
        case RedisServerMode.ReplicatedMode => config.useReplicatedServers().setNodeAddresses(redisConfig.endpoints.toList.asJava)
      }

      Redisson.create(config)
    }

    @SuppressWarnings(Array("OptionGet"))
    def client: F[Throwable, RedissonClient] = {
      // get and then update here to not lock with mutex when client already acquired and allow multi-thread access
      ref.get.flatMap {
        case Some(value) => F.pure(value)
        case None        =>
          ref.update {
            case Some(value) => F.pure(Some(value))
            case None        => createClient.map(Some(_))
          }.map(_.get)
      }
    }

    def close(): F[Nothing, Unit] = ref.get.flatMap {
      case Some(value) => F.sync(value.shutdown())
      case None        => F.unit
    }
  }

  object WrappedRedissonClient {
    def resource[F[+_, +_]: IO2: Primitives2](redisConfig: RedisConfig, clock: Clock1[Identity]): Lifecycle[F[Throwable, _], WrappedRedissonClient[F]] = {
      for {
        ref    <- Lifecycle.liftF(F.mkRefM(Option.empty[RedissonClient]))
        client <- Lifecycle.make {
          F.syncThrowable {
            WrappedRedissonClient(redisConfig, clock, ref)
          }
        }(_.close())
      } yield client
    }
  }

  /* use personalized redis clock to not memoize clock in testkit (if clocks is memoized we can not use shifted clocks) */
  final class Resource[F[+_, +_]: IO2: BlockingIO2: Primitives2](
    redisConfig: RedisConfig,
    clock: Clock1[Identity] @Id("redis"),
    metrics: Metrics[F],
    portCheck: PortCheck,
  ) extends Lifecycle.OfInner[F[Throwable, _], RedisComponent[F]]
    with IntegrationCheck[F[Throwable, _]] {
    override val lifecycle: Lifecycle[F[Throwable, _], RedisComponent[F]] = {
      WrappedRedissonClient.resource(redisConfig, clock).map {
        wrappedClient =>
          new RedisComponent[F] {
            def rawRequest[A](
              metric: String
            )(f: RedissonClient => F[Throwable, A]
            )(implicit
              saveCounter: MacroMetricRedisMeter[metric.type],
              saveTimer: MacroMetricRedisTimer[metric.type],
            ): F[Throwable, A] = F.shiftBlocking {
              wrappedClient.client.flatMap {
                client =>
                  metrics.timerAnd(metric) {
                    f(client).tapError(_ => metrics.mark(metric)(using saveCounter))
                  }(using saveTimer)
              }
            }
          }
      }
    }

    override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
      if (redisConfig.integrationCheck) {
        val checks = redisConfig.endpoints.map {
          endpoint =>
            portCheck.checkUri(URI.create(endpoint), 6379, "Redis connection.")
        }
        checks.collectFirst { case failure: ResourceCheck.Failure => failure }.getOrElse(ResourceCheck.Success())
      } else ResourceCheck.Success()
    }
  }
}
