package net.playq.tk.metrics.macrodefs

import java.io.{BufferedWriter, File, FileWriter}
import izumi.fundamentals.platform.language.Quirks.*
import net.playq.tk.metrics.base.MetricDef

import scala.quoted.{Quotes, Type}

object MacroMetricSaver {

  final val metricsDir        = "META-INF/metrics"
  final val defaultMetricRole = "default"

  def getRoles()(using quotes: Quotes): Set[String] = {
    import quotes.reflect.*
    import scala.reflect.Selectable.reflectiveSelectable
    val metricsRoleSet = CompilationInfo
      .asInstanceOf[{ def XmacroSettings: List[String] }] // workaround for @experimental
      .XmacroSettings
      .filter(_.startsWith("metricsRole="))
      .map(_.stripPrefix("metricsRole="))
      .flatMap(_.split(';'))
      .toSet
    if (metricsRoleSet.isEmpty)
      throw new IllegalArgumentException("Cannot find scalac option for metric role name, expected -Xmacro-settings:metricsRole=<role_name1;role_name2>")
    metricsRoleSet
  }

  def getConstantType[S: Type](className: String, discardName: String)(using quotes: Quotes): String = {
    import quotes.reflect.*

    def error(tpe0: TypeRepr): Nothing = {
      val tpe = tpe0.show
      report.errorAndAbort(
        s"""When materializing $className[$tpe],
           |Couldn't record metric for metric name `$tpe` - metric name should be a String Constant. It can't be an expression.
           |To disable metric recording for `$tpe`, import $discardName.* - a metric created like this will not be visible in MetricsApi""".stripMargin,
        Position.ofMacroExpansion,
      )
    }

    TypeRepr.of[S].dealias.simplified match {
      case c: ConstantType =>
        c.constant.value.toString
      case t: TermRef =>
        t.widenTermRefByName match {
          case c: ConstantType =>
            c.constant.value.toString
          case _ =>
            error(t)
        }
      case tpe0 =>
        error(tpe0)
    }
  }

  def writeToFile(payload: List[MetricDef])(using quotes: Quotes): Unit = {
    import quotes.reflect.*
    import scala.reflect.Selectable.reflectiveSelectable
    val classesDir = CompilationInfo
      .asInstanceOf[{ def XmacroSettings: List[String] }] // workaround for @experimental
      .XmacroSettings
      .find(_.startsWith("metricsDir="))
      .getOrElse(
        throw new IllegalArgumentException(
          "Cannot find scalac option for managed resource directory, expected -Xmacro-settings:metricsDir=<sbt:classDirectory in Compile>"
        )
      )
    val targetDir = s"${classesDir.stripPrefix("metricsDir=")}/$metricsDir"

    val position              = Position.ofMacroExpansion
    val currentSourceFileName = position.sourceFile.name.stripSuffix(".scala")
    val fileName              = s"${currentSourceFileName}_line_${position.startLine}.metrics.json"
    val file                  = new File(s"$targetDir/$fileName")

    new File(targetDir).mkdirs()
    if (!file.exists()) {
      file.createNewFile()
    }
    val bw = new BufferedWriter(new FileWriter(file, true))
    try {
      val strings = MetricDef.encodeAll(payload)
      bw.append(strings + "\n").discard()
    } finally {
      bw.close()
    }
  }
}
