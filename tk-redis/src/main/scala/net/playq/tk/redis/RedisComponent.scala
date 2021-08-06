package net.playq.tk.redis

import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, F, IO2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.metrics.Metrics
import net.playq.tk.metrics.{MacroMetricRedisMeter, MacroMetricRedisTimer}
import net.playq.tk.redis.config.RedisConfig
import redis.clients.jedis.{Jedis, JedisPool}

import java.net.URI

trait RedisComponent[F[_, _]] {
  def rawRequest[A](
    metric: String
  )(f: Jedis => F[Throwable, A]
  )(implicit
    saveCounter: MacroMetricRedisMeter[metric.type],
    saveTimer: MacroMetricRedisTimer[metric.type],
  ): F[Throwable, A]
}

object RedisComponent {

  final class Resource[F[+_, +_]: IO2: BlockingIO2](
    redisConfig: RedisConfig,
    metrics: Metrics[F],
    portCheck: PortCheck,
  ) extends Lifecycle.Of[F[Throwable, ?], RedisComponent[F]](for {
      jedisPool <- Lifecycle.fromAutoCloseable(F.syncThrowable(new JedisPool(URI.create(redisConfig.endpoint))))
    } yield new RedisComponent[F] {
      def rawRequest[A](
        metric: String
      )(f: Jedis => F[Throwable, A]
      )(implicit
        saveCounter: MacroMetricRedisMeter[metric.type],
        saveTimer: MacroMetricRedisTimer[metric.type],
      ): F[Throwable, A] = {
        F.shiftBlocking {
          F.syncThrowable(jedisPool.getResource)
            .bracketAuto(
              jedis =>
                metrics.withTimer(metric) {
                  f(jedis).tapError(_ => metrics.mark(metric)(saveCounter))
                }(saveTimer)
            )
        }
      }
    })
    with IntegrationCheck[F[Throwable, ?]] {
    override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
      if (redisConfig.integrationCheck) {
        portCheck.checkUri(URI.create(redisConfig.endpoint), 6379, "Redis connection.")
      } else ResourceCheck.Success()
    }
  }

}
