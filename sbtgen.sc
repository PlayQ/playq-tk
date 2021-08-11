#!/bin/sh
coursier launch com.lihaoyi:ammonite_2.13.0:1.6.9 --fork -M ammonite.Main -- sbtgen.sc $*
exit
!#

import java.nio.file.{FileSystems, Files}
import $ivy.`io.7mind.izumi.sbt:sbtgen_2.13:0.0.79`, izumi.sbtgen._, izumi.sbtgen.model._, izumi.sbtgen.model.LibSetting.Exclusion
import ProjectBuilder.ProjectDeps._

val settings = GlobalSettings(
  groupId = "net.playq",
  sbtVersion = None,
)

@main
def entrypoint(args: String*) = Entrypoint.main(ProjectBuilder.root, settings, Seq("-o", ".") ++ args)

object Targets {
  final val scala213 = ScalaVersion(getScalaVersion("213"))

  private def getScalaVersion(v: String): String = {
    s"""scala_$v.*"(.*)"""".r
      .findFirstMatchIn(
        Files.readString(FileSystems.getDefault.getPath("project/Versions.scala"))
      ).map(_.group(1)).getOrElse(throw new RuntimeException(s"Couldn't get `scala_$v` version from project/Versions.scala"))
  }
  val targetScala = Seq(scala213)
  private val jvmPlatform = PlatformEnv(
    platform = Platform.Jvm,
    language = targetScala,
    settings = Seq(
      "scalacOptions" ++= Defaults.Scala213Options.filterNot(Set[Const]("-Xsource:3", "-P:kind-projector:underscore-placeholders")),
      "scalacOptions" ++= Seq(
        "-Wconf:msg=kind-projector:silent",
        "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent",
      ),
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
}


object ProjectBuilder {

  object V {
    val izumi = Version.VExpr("V.izumi_version")
    val idealingua = Version.VExpr("V.izumi_idealingua_version")
    val d4s = Version.VExpr("V.d4s_version")

    val cats = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_core_version")
    val cats_effect = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_effect_version")

    val zio = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.dev_zio_zio_version")
    val zio_interop_cats = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.dev_zio_zio_interop_cats_version")

    val circe = Version.VExpr("V.circe")
    val circe_generic_extras = Version.VExpr("V.circe_generic_extras")
    val circe_derivation = Version.VExpr("V.circe_derivation")
    val circe_pointer = Version.VExpr("V.circe_pointer")

    val doobie = Version.VExpr("V.doobie")

    val aws_sdk = Version.VExpr("V.aws_java_sdk")
  }

  object ProjectDeps {
    final val distage_framework     = Library("io.7mind.izumi", "distage-framework", V.izumi, LibraryType.Auto)
    final val distage_plugins       = Library("io.7mind.izumi", "distage-extension-plugins", V.izumi, LibraryType.Auto)
    final val distage_config        = Library("io.7mind.izumi", "distage-extension-config", V.izumi, LibraryType.Auto)
    final val distage_framework_api = Library("io.7mind.izumi", "distage-framework-api", V.izumi, LibraryType.Auto)
    final val distage_testkit       = Library("io.7mind.izumi", "distage-testkit-scalatest", V.izumi, LibraryType.Auto)
    final val distage_docker        = Library("io.7mind.izumi", "distage-framework-docker", V.izumi, LibraryType.Auto)

    final val idealingua_v1_runtime_rpc_http4s =
      Library("io.7mind.izumi", "idealingua-v1-runtime-rpc-http4s", V.idealingua, LibraryType.Auto)
    final val idealingua_v1_runtime_rpc_scala =
      Library("io.7mind.izumi", "idealingua-v1-runtime-rpc-scala", V.idealingua, LibraryType.Auto)
    final val idealingua_v1_model = Library("io.7mind.izumi", "idealingua-v1-model", V.idealingua, LibraryType.Auto)

    final val circe = Seq(
      Library("io.circe", "circe-core", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-generic", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-generic-extras", V.circe_generic_extras, LibraryType.Auto),
      Library("io.circe", "circe-parser", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-literal", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-derivation", V.circe_derivation, LibraryType.Auto),
      Library("io.circe", "circe-pointer", V.circe_pointer, LibraryType.Auto),
      Library("io.circe", "circe-pointer-literal", V.circe_pointer, LibraryType.Auto),
    )

    final val scala_reflect = Library("org.scala-lang", "scala-reflect", Version.VExpr("scalaVersion.value"), LibraryType.Invariant) in Scope.Provided.all

    final val logstage_rendering_circe = Library("io.7mind.izumi", "logstage-rendering-circe", V.izumi, LibraryType.Auto)
    final val logstage_adapter_slf4j   = Library("io.7mind.izumi", "logstage-adapter-slf4j", V.izumi, LibraryType.Auto)
    final val logstage_core            = Library("io.7mind.izumi", "logstage-core", V.izumi, LibraryType.Auto)

    final val cats_core   = Library("org.typelevel", "cats-core", V.cats, LibraryType.Auto)
    final val cats_effect = Library("org.typelevel", "cats-effect", V.cats_effect, LibraryType.Auto)
    final val cats_all    = Seq(cats_core, cats_effect)
    final val zio_core    = Library("dev.zio", "zio", V.zio, LibraryType.Auto)
    final val zio_interop = Library("dev.zio", "zio-interop-cats", V.zio_interop_cats, LibraryType.Auto)

    final val scalatest                = Library("org.scalatest", "scalatest", Version.VExpr("V.scalatest"), LibraryType.Auto)
    final val scalatestplus_scalacheck = Library("org.scalatestplus", "scalacheck-1-15", Version.VExpr("V.scalatestplus_scalacheck"), LibraryType.Auto)
    final val fundamentals_bio         = Library("io.7mind.izumi", "fundamentals-bio", V.izumi, LibraryType.Auto)
    final val fundamentals_platform    = Library("io.7mind.izumi", "fundamentals-platform", V.izumi, LibraryType.Auto)

    final val projector = Library("org.typelevel", "kind-projector", Version.VExpr("V.kind_projector"), LibraryType.Invariant)
      .more(LibSetting.Raw("""cross CrossVersion.constant("2.13.5")"""))

    final val fs2_io   = Library("co.fs2", "fs2-io", Version.VExpr("V.fs2"), LibraryType.Auto)
    final val magnolia = Library("com.propensive", "magnolia", Version.VExpr("V.magnolia"), LibraryType.Auto)

    final val doobie_core      = Library("org.tpolecat", "doobie-core", V.doobie, LibraryType.Auto)
    final val doobie_postgres  = Library("org.tpolecat", "doobie-postgres", V.doobie, LibraryType.Auto)
    final val doobie_circe     = Library("org.tpolecat", "doobie-postgres-circe", V.doobie, LibraryType.Auto)
    final val doobie_hikari    = Library("org.tpolecat", "doobie-hikari", V.doobie, LibraryType.Auto)
    final val doobie_scalatest = Library("org.tpolecat", "doobie-scalatest", V.doobie, LibraryType.Auto)
    final val doobie_all = Seq(doobie_core, doobie_hikari, doobie_postgres, doobie_circe)

    final val jwt_scala = Seq(
      Library("com.pauldijou", "jwt-circe", Version.VExpr("V.jwt_scala"), LibraryType.Auto),
      Library("org.bouncycastle", "bcpkix-jdk15on", Version.VExpr("V.bouncycastle"), LibraryType.Invariant),
    )

    final val scalacheck           = Library("org.scalacheck", "scalacheck", Version.VExpr("V.scalacheck"), LibraryType.Auto)
    final val scalacheck_shapeless = Library("com.github.alexarchambault", "scalacheck-shapeless_1.14", Version.VExpr("V.scalacheck_shapeless"), LibraryType.Auto)

    final val kafka_client = Library("org.apache.kafka", "kafka-clients", Version.VExpr("V.kafka"), LibraryType.Invariant)
      .more(
        LibSetting.Exclusions(
          Seq(
            Exclusion("org.slf4j", "log4j-over-slf4j"),
            Exclusion("javax.jms", "jms"),
            Exclusion("com.sun.jdmk", "jmxtools"),
            Exclusion("com.sun.jmx", "jmxri"),
            Exclusion("log4j", "log4j"),
          )
        )
      )

    final val aws_s3 = Library("software.amazon.awssdk", "s3", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_dynamo = Library("software.amazon.awssdk", "dynamodb", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_impl_apache = Library("software.amazon.awssdk", "apache-client", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_sqs = Library("software.amazon.awssdk", "sqs", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_ses = Library("software.amazon.awssdk", "ses", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_sts = Library("software.amazon.awssdk", "sts", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_lambda = Library("software.amazon.awssdk", "lambda", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_sagemaker = Library("software.amazon.awssdk", "sagemaker", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val aws_costexplorer = Library("software.amazon.awssdk", "costexplorer", V.aws_sdk, LibraryType.Invariant)
      .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

    final val curator = Seq(
      Library("org.apache.curator", "curator-recipes", Version.VExpr("V.curator"), LibraryType.Invariant)
        .more(LibSetting.Exclusions(Seq(Exclusion("org.apache.zookeeper", "zookeeper")))),
      Library("org.apache.zookeeper", "zookeeper", Version.VExpr("V.zookeeper"), LibraryType.Invariant)
        .more(LibSetting.Exclusions(Seq(Exclusion("org.slf4j", "slf4j-log4j12")))),
    )
    final val jedis      = Library("redis.clients", "jedis", Version.VExpr("V.jedis"), LibraryType.Invariant)
    final val guava = Library("com.google.guava", "guava", Version.VExpr("V.guava"), LibraryType.Invariant)

    final val auth0_jwks = Library("com.auth0", "jwks-rsa", Version.VExpr("V.auth0_jwks"), LibraryType.Invariant)
    final val tfa = Library("com.j256.two-factor-auth", "two-factor-auth", Version.VExpr("V.tfa"), LibraryType.Invariant)

    final val log4j_to_slf4j   = Library("org.apache.logging.log4j", "log4j-to-slf4j", Version.VExpr("V.log4j_to_slf4j"), LibraryType.Invariant)
    final val rocksdb = Library("org.rocksdb", "rocksdbjni", Version.VExpr("V.rocksdb"), LibraryType.Invariant)
  }

  object ProjectSettings {
    final val sharedImports = Seq(
      Import("IzumiPublishingPlugin.Keys._"),
      Import("IzumiPublishingPlugin.autoImport._"),
      Import("IzumiConvenienceTasksPlugin.Keys._"),
      Import("sbtrelease.ReleaseStateTransformations._"),
    )

    final val rootProjectSettings = Defaults.SharedOptions ++ Seq(
      "fork" in SettingScope.Raw("Global") := false,

      "crossScalaVersions" := "Nil".raw,
      "scalaVersion" := Targets.targetScala.head.value,
      "coverageOutputXML" in SettingScope.Raw("Global") := true,
      "coverageOutputHTML" in SettingScope.Raw("Global") := true,
      "organization" in SettingScope.Raw("Global") := "net.playq",

      "sonatypeProfileName" := "net.playq",
      "sonatypeSessionName" := """s"[sbt-sonatype] ${name.value} ${version.value} ${java.util.UUID.randomUUID}"""".raw,
      "publishTo" in SettingScope.Build :=
        """
          |(if (!isSnapshot.value) {
          |    sonatypePublishToBundle.value
          |  } else {
          |    Some(Opts.resolver.sonatypeSnapshots)
          |})
          |""".stripMargin.raw,

      "credentials" in SettingScope.Build += """Credentials(file(".secrets/credentials.sonatype-nexus.properties"))""".raw,
      "homepage" in SettingScope.Build := """Some(url("https://www.playq.com/"))""".raw,
      "licenses" in SettingScope.Build := """Seq("Apache-License" -> url("https://opensource.org/licenses/Apache-2.0"))""".raw,

      "developers" in SettingScope.Build :=
        """List(
          Developer(id = "playq", name = "PlayQ", url = url("https://github.com/PlayQ"), email = "platform-team@playq.net"),
        )""".raw,

      "scmInfo" in SettingScope.Build := """Some(ScmInfo(url("https://github.com/PlayQ/playq-tk"), "scm:git:https://github.com/PlayQ/playq-tk.git"))""".raw,
      "scalacOptions" in SettingScope.Build += s"""${"\"" * 3}-Xmacro-settings:scalatest-version=${Version.VExpr("V.scalatest")}${"\"" * 3}""".raw,

      "scalacOptions" in SettingScope.Build ++= Seq(
        """s"-Xmacro-settings:product-name=playq-tk"""".raw,
        """s"-Xmacro-settings:product-group=playq-tk"""".raw,
        """s"-Xmacro-settings:product-version=${ThisBuild / version}"""".raw,
      ),

      "releaseProcess" := """Seq[ReleaseStep](
                            |  checkSnapshotDependencies,
                            |  inquireVersions,
                            |  runClean,
                            |  runTest,
                            |  setReleaseVersion,
                            |  commitReleaseVersion,
                            |  tagRelease,
                            |  //publishArtifacts,
                            |  setNextVersion,
                            |  commitNextVersion,
                            |  pushChanges
                            |)""".stripMargin.raw,

      // sbt 1.3.0
      "onChangedBuildSource" in SettingScope.Raw("Global") := "ReloadOnSourceChanges".raw,
    )

    final val sharedSettings = Seq(
      "scalacOptions" in SettingScope.Compile += """s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}"""".raw,

      "scalacOptions" in SettingScope.Compile += """s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}"""".raw,

      "testOptions" in SettingScope.Test += """Tests.Argument("-oDF")""".raw,
      "logBuffered" in SettingScope.Test := true,

      "testOptions" in SettingScope.Test += """Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit")""".raw,

      "resolvers" += "DefaultMavenRepository".raw,
      "resolvers" += "Opts.resolver.sonatypeSnapshots".raw,
    )

    final val crossScalaSources = Defaults.CrossScalaSources
  }

  object lib {
    final val tkLauncherCore = ArtifactId("tk-launcher-core")
    final val tkHttpCore     = ArtifactId("tk-http-core")
    final val tkLoadtool     = ArtifactId("tk-loadtool")
    final val tkTest         = ArtifactId("tk-test")
    final val tkHealth       = ArtifactId("tk-health")
    final val tkAuthTools    = ArtifactId("tk-auth-tools")
    final val tkUtil         = ArtifactId("tk-util")

    final val tkAws          = ArtifactId("tk-aws")
    final val tkAwsS3        = ArtifactId("tk-aws-s3")
    final val tkAwsSes       = ArtifactId("tk-aws-ses")
    final val tkAwsSts       = ArtifactId("tk-aws-sts")
    final val tkAwsSqs       = ArtifactId("tk-aws-sqs")
    final val tkAwsCost      = ArtifactId("tk-aws-cost")
    final val tkAwsLambda    = ArtifactId("tk-aws-lambda")
    final val tkAwsSagemaker = ArtifactId("tk-aws-sagemaker")

    final val d4s      = ArtifactId("d4s")
    final val d4s_test = ArtifactId("d4s-test")
    final val d4s_circe = ArtifactId("d4s-circe")

    final val tkMetrics      = ArtifactId("tk-metrics")
    final val tkMetricsApi   = ArtifactId("tk-metrics-api")

    final val tkKafka        = ArtifactId("tk-kafka")
    final val fs2KafkaClient = ArtifactId("fs2-kafka-client")

    final val tkRedis      = ArtifactId("tk-redis")
    final val tkZookeeper  = ArtifactId("tk-zookeeper")
    final val tkPostgres   = ArtifactId("tk-postgres")
    final val tkCacheGuava = ArtifactId("tk-cache-guava")
    final val tkDocker     = ArtifactId("tk-docker")
    final val tkRocksDB    = ArtifactId("tk-rocksdb")

    final val tkImplicits    = ArtifactId("tk-implicits")
  }

  final lazy val playq_tk_agg = Aggregate(
    name = ArtifactId("tk"),
    artifacts = Seq(
      Artifact(
        name = lib.tkMetrics,
        libs = Seq(
          distage_framework,
        ).map(_ in Scope.Compile.all) ++
          Seq(scala_reflect) ++
          circe.map(_ in Scope.Optional.all) ++
          Seq(
          scalatest,
          scalatestplus_scalacheck,
          scalacheck_shapeless
        ).map(_ in Scope.Test.all),
        depends = Seq.empty,
      ),
      Artifact(
        name = lib.tkMetricsApi,
        libs = Seq(distage_plugins, distage_framework, idealingua_v1_runtime_rpc_http4s)
          .map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkMetrics,
          lib.tkImplicits,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkUtil,
        libs = Seq(
          distage_plugins,
          distage_framework,
          zio_core,
        ).map(_ in Scope.Compile.all),
        depends = Seq(lib.tkTest in Scope.Test.all),
      ),
      Artifact(
        name = lib.tkPostgres,
        libs = Seq(doobie_scalatest in Scope.Test.all) ++
          (doobie_all ++ Seq(distage_plugins, distage_config, distage_framework_api))
            .map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkHealth,
          lib.tkTest,
          lib.tkImplicits,
          lib.tkMetricsApi,
          lib.tkDocker,
          lib.tkUtil,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkLauncherCore,
        libs = Seq(
          distage_framework,
          logstage_rendering_circe,
          logstage_adapter_slf4j,
          log4j_to_slf4j,
        ).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkImplicits,
          lib.tkMetricsApi,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkHttpCore,
        libs = Seq(
          distage_framework,
          logstage_rendering_circe,
          idealingua_v1_runtime_rpc_scala,
          idealingua_v1_runtime_rpc_http4s,
        ).++(jwt_scala).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkMetricsApi,
          lib.tkImplicits,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkImplicits,
        libs = Seq(
          distage_plugins,
          distage_framework,
          cats_effect,
          cats_core,
          zio_interop,
          zio_core,
          logstage_core,
          fs2_io,
          magnolia,
        ).map(_ in Scope.Compile.all),
        depends = Seq.empty,
      ),
      Artifact(
        name = lib.tkAws,
        libs = Seq(
          aws_sts,
          distage_framework,
        ),
        depends = Seq.empty,
      ),
      Artifact(
        name = lib.tkAwsS3,
        libs = Seq(aws_s3, distage_config).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkAws,
          lib.tkUtil,
          lib.tkMetricsApi,
          lib.tkHealth,
          lib.tkImplicits,
          lib.tkTest,
          lib.tkDocker,
        ),
      ),
      Artifact(
        name = lib.tkAwsSts,
        libs = Seq(),
        depends = Seq(
          lib.tkAws,
          lib.tkMetricsApi,
          lib.tkTest,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkAwsSes,
        libs = Seq(aws_ses, distage_config),
        depends = Seq(
          lib.tkAws,
          lib.tkUtil,
          lib.tkHealth,
          lib.tkImplicits,
          lib.tkTest,
          lib.tkDocker,
        ),
      ),
      Artifact(
        name = lib.tkAwsSqs,
        libs = (Seq(aws_sqs, fs2_io) ++ cats_all).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkAws,
          lib.tkUtil,
          lib.tkHealth,
          lib.tkImplicits,
          lib.tkMetricsApi,
          lib.tkTest,
          lib.tkDocker,
        ),
      ),
      Artifact(
        name = lib.tkAwsCost,
        libs = Seq(
          aws_costexplorer
        ).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkAws,
          lib.tkTest,
          lib.tkMetricsApi,
          lib.tkUtil,
        ),
      ),
      Artifact(
        name = lib.tkAwsLambda,
        libs = Seq(
          aws_lambda
        ).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkAws,
          lib.tkTest,
          lib.tkMetricsApi,
          lib.tkUtil,
        ),
      ),
      Artifact(
        name = lib.tkAwsSagemaker,
        libs = Seq(
          aws_sagemaker
        ).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkAwsS3,
          lib.tkUtil,
        ),
      ),
      Artifact(
        name = lib.tkAuthTools,
        libs = Seq(auth0_jwks, tfa),
        depends = Seq(
          lib.tkHttpCore,
          lib.tkImplicits,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name    = lib.tkLoadtool,
        libs    = Seq(distage_framework).map(_ in Scope.Compile.all),
        depends = Seq(lib.tkUtil, lib.tkImplicits).map(_ in Scope.Compile.all) ++ Seq(lib.tkTest in Scope.Test.all),
      ),
      Artifact(
        name = lib.tkTest,
        libs = Seq(
          distage_framework,
          distage_testkit,
          fundamentals_platform,
          distage_docker,
          distage_plugins,
          distage_config,
          scalatest,
          scalatestplus_scalacheck,
          scalacheck,
          scalacheck_shapeless,
          log4j_to_slf4j,
        ).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkImplicits,
          lib.tkMetricsApi,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkDocker,
        libs = Seq(distage_framework, distage_docker).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkImplicits,
          lib.tkUtil,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name    = lib.fs2KafkaClient,
        libs    = (Seq(kafka_client, fs2_io) ++ cats_all).map(_ in Scope.Compile.all),
        depends = Seq(lib.tkImplicits in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkKafka,
        libs = Seq(kafka_client).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.fs2KafkaClient,
          lib.tkZookeeper,
          lib.tkDocker,
          lib.tkMetrics,
        ),
      ),
      Artifact(
        name = lib.tkZookeeper,
        libs = (Seq(distage_framework) ++ curator).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkUtil,
          lib.tkImplicits,
          lib.tkTest,
          lib.tkDocker,
        ),
      ),
      Artifact(
        name    = lib.tkCacheGuava,
        libs    = Seq(guava),
        depends = Seq(lib.tkImplicits in Scope.Compile.all, lib.tkTest in Scope.Test.all),
      ),
      Artifact(
        name = lib.tkRedis,
        libs = Seq(jedis).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkImplicits,
          lib.tkMetricsApi,
          lib.tkDocker,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.tkHealth,
        libs = Seq.empty,
        depends = Seq(
          lib.tkImplicits
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.d4s,
        libs = Seq(
          aws_dynamo,
          aws_impl_apache,
          magnolia,
        ).map(_ in Scope.Compile.all) ++
          Seq(scala_reflect),
        depends = Seq(
          lib.tkAws,
          lib.tkHealth,
          lib.tkImplicits,
          lib.tkDocker,
          lib.tkMetrics,
        ).map(_ in Scope.Compile.all) ++ Seq(lib.tkTest in Scope.Test.all),
      ),
      Artifact(
        name = lib.d4s_test,
        libs = Seq.empty,
        depends = Seq(
          lib.d4s,
          lib.tkTest,
          lib.tkImplicits,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = lib.d4s_circe,
        libs = circe.map(_ in Scope.Compile.all),
        depends = Seq(
          lib.d4s in Scope.Compile.all,
          lib.tkTest in Scope.Test.all,
        ),
      ),
      Artifact(
        name = lib.tkRocksDB,
        libs = Seq(rocksdb, distage_framework).map(_ in Scope.Compile.all),
        depends = Seq(
          lib.tkTest,
          lib.tkImplicits,
          lib.tkDocker,
        ).map(_ in Scope.Compile.all),
      ),
    ),
    pathPrefix       = Seq("."),
    groups           = Set(Group("tk")),
    defaultPlatforms = Targets.jvm,
    sharedSettings = Seq(
      "scalacOptions" in SettingScope.Compile += "-Xmacro-settings:metricsRole=default"
    ),
  )

  final val root = Project(
    name = ArtifactId("playq-tk"),
    aggregates = {
      Seq(playq_tk_agg)
    },
    sharedSettings = ProjectSettings.sharedSettings ++ ProjectSettings.crossScalaSources,
    sharedAggSettings = Seq(
      "crossScalaVersions" := "Nil".raw,
    ),
    rootSettings = ProjectSettings.rootProjectSettings,
    imports = ProjectSettings.sharedImports,
    globalLibs = Seq(
      (projector in Scope.Compile.all).copy(compilerPlugin = true),
    ),
    rootPlugins = Plugins(
      enabled = Seq(
        Plugin("IzumiPublishingPlugin"),
        Plugin("IzumiResolverPlugin"),
        Plugin("IzumiConvenienceTasksPlugin"),
        Plugin("SbtgenVerificationPlugin"),
      ),
    ),
    globalPlugins = Plugins(
      enabled = Seq(
        Plugin("IzumiPublishingPlugin"),
        Plugin("IzumiResolverPlugin"),
      ),
    ),
    appendPlugins = Defaults.SbtGenPlugins ++ Seq(
      SbtPlugin("io.7mind.izumi", "sbt-izumi-deps", Version.VExpr("PV.izumi_deps")),
    ),
  )
}
