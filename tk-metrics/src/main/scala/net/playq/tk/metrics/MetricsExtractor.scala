package net.playq.tk.metrics

import cats.instances.list.*
import cats.syntax.foldable.*
import io.github.classgraph.ClassGraph
import izumi.distage.roles.model.meta.RolesInfo
import logstage.IzLogger
import net.playq.tk.metrics.base.MetricDef
import net.playq.tk.metrics.macrodefs.MacroMetricSaver
import net.playq.tk.metrics.macrodefs.MacroMetricSaver.metricsDir

import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*

final class MetricsExtractor(rolesInfo: RolesInfo, logger: IzLogger) {

  final val allRoles = s"${rolesInfo.requiredRoleBindings.map(_.descriptor.id).mkString(";")}${MacroMetricSaver.defaultMetricRole}"

  def collectMetrics: Set[MetricDef] = collectMetrics(identity)

  def collectMetrics(tweakRole: String => String): Set[MetricDef] = {
    val scan         = new ClassGraph().scan()
    val resourceList = scan.getResourcesMatchingPattern(s"$metricsDir/.*".r.pattern)
    try {
      val (errors, rawFetched) = resourceList.asScala.toList.flatMap {
        case res if !res.getPath.endsWith("metrics.json") =>
          val filename = res.getPath
          logger.crit(s"Found a junk file with $filename in ${metricsDir -> "metricsDir"} - filename does not end with `metrics.json`, Skipping.")
          Nil
        case res =>
          val bytes   = res.load()
          val content = new String(bytes, UTF_8)
          content.linesIterator.filter(_.nonEmpty).map(MetricDef.decode)
      }.partitionEither(identity)

      reportErrors(errors)
      rawFetched.toSet.filter(m => allRoles.contains(tweakRole(m.role)))
    } finally {
      resourceList.close()
      scan.close()
    }
  }

  private def reportErrors(errors: List[Throwable]): Unit = {
    if (errors.nonEmpty) {
      logger.crit(s"Couldn't read some of the metrics - $errors")
    }
  }
}
