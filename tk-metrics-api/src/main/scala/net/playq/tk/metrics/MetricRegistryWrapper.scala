package net.playq.tk.metrics

/**
  * This is a dirty workaround to inject MetricRegistry into HikaryCP without making PG artifact dependant on tg-metrics-dropwizard
  */
final case class MetricRegistryWrapper(registry: Option[AnyRef])
