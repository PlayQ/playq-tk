package net.playq.tk.metrics

import izumi.distage.model.definition.Lifecycle
import izumi.logstage.api.IzLogger

trait MetricsReportingComponent

object MetricsReportingComponent {
  final class Dummy(logger: IzLogger) extends Lifecycle.MutableNoClose[MetricsReportingComponent] with MetricsReportingComponent {
    override def acquire: Unit = {
      logger.debug("starting dummy metrics component")
    }
  }
}
