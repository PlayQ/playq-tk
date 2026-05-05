package net.playq.tk.aws.cost

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.retry.Scheduler2
import izumi.functional.bio.{BlockingIO2, IO2}
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.metrics.Metrics
import net.playq.tk.quantified.SyncThrowable
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.costexplorer.CostExplorerClient

import scala.util.chaining.*

trait CostComponent[F[_, _]] {
  def resourceClient: Lifecycle[F[Throwable, _], CostClient[F]]
}

object CostComponent {

  final class Impl[F[+_, +_]: IO2: SyncThrowable: BlockingIO2: Scheduler2](
    credentialsConfig: AwsCredentialsConfig,
    metrics: Metrics[F],
  ) extends CostComponent[F] {

    override def resourceClient: Lifecycle[F[Throwable, _], CostClient[F]] = {
      Lifecycle.fromAutoCloseable {
        CostExplorerClient
          .builder()
          .pipe(b => credentialsConfig.region.fold(b)(r => b.region(Region.of(r))))
          .pipe(b => credentialsConfig.provider.fold(b)(b.credentialsProvider))
          .build()
      }.toEffect[F[Throwable, _]].map(new CostClient.Impl[F](_, metrics))
    }
  }

}
