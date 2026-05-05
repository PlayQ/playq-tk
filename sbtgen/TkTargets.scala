package tk.sbtgen

import izumi.sbtgen._
import izumi.sbtgen.model._

import java.nio.file.{FileSystems, Files}

object TkTargets {
  final val scala213 = ScalaVersion(getScalaVersion("213"))
  final val scala300 = ScalaVersion(getScalaVersion("300"))

  private def getScalaVersion(v: String): String = {
    s"""scala_$v.*"(.*)"""".r
      .findFirstMatchIn(
        Files.readString(FileSystems.getDefault.getPath("project/TkVersions.scala"))
      ).map(_.group(1)).getOrElse(throw new RuntimeException(s"Couldn't get `scala_$v` version from project/TkVersions.scala"))
  }

  val targetScala = Seq(scala300, scala213)
  //  val targetScala = Seq(scala213, scala300)

  val scala213Options = Defaults.Scala213Options.map[Const] {
    case Const.CRaw(s"$_-Wconf:any:warning$_") => "-Wconf:any:error"
    case Const.CString(s"-release:$_")         => "-release:25"
    // https://github.com/scala/bug/issues/13041
    case Const.CString("-Wunused:_") => "-Wunused:-patvars,_"
    case x                           => x
  } ++ Seq[Const]("-Wconf:cat=lint-eta-sam:silent")

  val scala300Options = Defaults.Scala3Options.map[Const] {
    // todo: temporary disable critical warnings
    case Const.CRaw(s"$_-Wconf:any:warning$_")         => "-Wconf:any:warning"
    case Const.CString(s"-release:$_")                 => "-release:25"
    case Const.CString("-Ykind-projector:underscores") => "-Xkind-projector:underscores"
    case x                                             => x
  } ++ Seq[Const](
    "-source:3.7",
    "-Wconf:msg=Ignoring..this..qualifier:silent",
    "-Wconf:msg=Alphanumeric.method.contains.is.not.declared.infix:silent",
    "-Wconf:msg=Alphanumeric.method.in.is.not.declared.infix:silent",
    "-Wconf:msg=.=._..has.been.deprecated..use..=.uninitialized:silent",
  )

  private val jvmPlatform = PlatformEnv(
    platform = Platform.Jvm,
    language = targetScala,
    settings = Seq(
      "scalacOptions" ++= Seq(
        SettingKey(Some(scala213), None) := scala213Options,
        SettingKey(Some(scala300), None) := scala300Options,
        SettingKey.Default := Const.EmptySeq,
      ),
      "scalacOptions" ++= Seq("-Wconf:msg=nowarn:silent"),
      "scalacOptions" ++= Seq(
        SettingKey(None, release = Some(true)) := Seq(
          "-opt:l:inline",
          "-opt-inline-from:izumi.**",
          "-opt-inline-from:net.playq.**",
        ),
        SettingKey.Default := Const.EmptySeq,
      ),
    ),
  )

  final val jvm = Seq(jvmPlatform)

  private val jsPlatform = PlatformEnv(
    platform = Platform.Js,
    language = targetScala,
    settings = Seq(
      "scalacOptions" ++= Seq("-Wconf:msg=nowarn:silent"),
      "coverageEnabled" := false,
      "scalaJSLinkerConfig" in (
        SettingScope.Project,
        Platform.Js,
      ) := "scalaJSLinkerConfig.value.withBatchMode(true).withModuleKind(ModuleKind.CommonJSModule).withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES5_1).withAvoidClasses(true))".raw,
      "libraryDependencies" += """("org.scala-js" %%% "scalajs-java-securerandom" % V.scalajs_java_securerandom_version).cross(CrossVersion.for3Use2_13)""".raw,
      "scalacOptions" ++= Seq(
        SettingKey(Some(scala213), None) := scala213Options,
        SettingKey(Some(scala300), None) := scala300Options,
        SettingKey.Default := Const.EmptySeq,
      ),
    ),
  )

  final val crossJs = Seq(jvmPlatform, jsPlatform)
  final val jsOnly  = Seq(jsPlatform)
}
