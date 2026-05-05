package tk.sbtgen

import izumi.sbtgen._
import izumi.sbtgen.model.{SettingScope, _}
import tk.sbtgen.TkDeps._

final class TkBuild(rootOf: String => String) {
  object lib {
    final val tkLauncherCore = ArtifactId("tk-launcher-core")
    final val tkHttpCore     = ArtifactId("tk-http-core")
    final val tkHttpWs       = ArtifactId("tk-http-ws")
    final val tkHttpWsModels = ArtifactId("tk-http-ws-models")
    final val tkLoadtool     = ArtifactId("tk-loadtool")
    final val tkTest         = ArtifactId("tk-test")
    final val tkHealth       = ArtifactId("tk-health")
    final val tkAuthTools    = ArtifactId("tk-auth-tools")
    final val tkUtil         = ArtifactId("tk-util")
    final val tkOrc          = ArtifactId("tk-orc")
    final val tkParquet      = ArtifactId("tk-parquet")

    final val tkAws          = ArtifactId("tk-aws")
    final val tkAwsS3        = ArtifactId("tk-aws-s3")
    final val tkAwsSes       = ArtifactId("tk-aws-ses")
    final val tkAwsSts       = ArtifactId("tk-aws-sts")
    final val tkAwsSqs       = ArtifactId("tk-aws-sqs")
    final val tkAwsSns       = ArtifactId("tk-aws-sns")
    final val tkAwsCost      = ArtifactId("tk-aws-cost")
    final val tkAwsLambda    = ArtifactId("tk-aws-lambda")
    final val tkAwsSagemaker = ArtifactId("tk-aws-sagemaker")

    final val d4s      = ArtifactId("d4s")
    final val d4sTest  = ArtifactId("d4s-test")
    final val d4sCirce = ArtifactId("d4s-circe")

    final val tkMetrics    = ArtifactId("tk-metrics")
    final val tkMetricsApi = ArtifactId("tk-metrics-api")

    final val tkKafka = ArtifactId("tk-kafka")

    final val tkSynchronizer         = ArtifactId("tk-synchronizer")
    final val tkSynchronizerZk       = ArtifactId("tk-synchronizer-zk")
    final val tkSynchronizerPostgres = ArtifactId("tk-synchronizer-postgres")

    final val tkRedis      = ArtifactId("tk-redis")
    final val tkZookeeper  = ArtifactId("tk-zookeeper")
    final val tkPostgres   = ArtifactId("tk-postgres")
    final val tkCacheGuava = ArtifactId("tk-cache-guava")
    final val tkDocker     = ArtifactId("tk-docker")
    final val tkRocksDB    = ArtifactId("tk-rocksdb")

    final val tkImplicits   = ArtifactId("tk-implicits")
    final val tkCompression = ArtifactId("tk-compression")

    final val tkNpmResolver = ArtifactId("tk-npm-resolver")

    final val basePath = Seq(rootOf("playq-tk"))
  }

  object ProjectSettings {
    final val crossScalaSources = Defaults.CrossScalaPlusSources

    final val sharedImports = Seq(
      Import("IzumiPublishingPlugin.Keys._"),
      Import("IzumiPublishingPlugin.autoImport._"),
      Import("IzumiConvenienceTasksPlugin.Keys._"),
      Import("sbtrelease.ReleaseStateTransformations._"),
      Import("Tk.V"),
    )

    final val rootProjectSettings = Defaults.RootOptions ++ Defaults.SbtMetaRootOptions ++ Seq(
      "fork" in SettingScope.Raw("Global") := false,
      "coverageOutputXML" in SettingScope.Raw("Global") := true,
      "coverageOutputHTML" in SettingScope.Raw("Global") := true,
      "organization" in SettingScope.Raw("Global") := "net.playq",
      "crossScalaVersions" := "Nil".raw,
      "publishTo" in SettingScope.Build :=
        """{
          |  // https://github.com/sbt/sbt/issues/8131
          |  if (isSnapshot.value) {
          |    Some(
          |      "central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"
          |    )
          |  } else {
          |    localStaging.value
          |  }
          |}
          |""".stripMargin.raw,
      "credentials" in SettingScope.Build ++=
        """{
          |  val credTarget = file(".") / ".secrets" / "credentials.sonatype-nexus.properties"
          |  if (credTarget.exists) {
          |    Seq(Credentials(credTarget))
          |  } else {
          |    Seq.empty
          |  }
          |}""".stripMargin.raw,
      "homepage" in SettingScope.Build := """Some(url("https://www.playq.com/"))""".raw,
      "licenses" in SettingScope.Build := """Seq("Apache-License" -> url("https://opensource.org/licenses/Apache-2.0"))""".raw,
      "developers" in SettingScope.Build :=
        """List(
          Developer(id = "playq", name = "PlayQ", url = url("https://github.com/PlayQ"), email = "platform-team@playq.net"),
        )""".raw,
      "scmInfo" in SettingScope.Build := """Some(ScmInfo(url("https://github.com/PlayQ/playq-tk"), "scm:git:https://github.com/PlayQ/playq-tk.git"))""".raw,
      "scalacOptions" in SettingScope.Build ++= Seq(
        """s"-Xmacro-settings:product-name=playq-tk"""".raw,
        """s"-Xmacro-settings:product-group=${organization.value}"""".raw,
        """s"-Xmacro-settings:product-version=${(ThisBuild / version).value}"""".raw,
      ),
      "resolvers" in SettingScope.Build += "DefaultMavenRepository".raw,
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

      // override netty version to keep single stable dependency
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-buffer" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-base" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-compression" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-dns" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-http2" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-http" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-marshaling" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-protobuf" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-codec-socks" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-common" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-handler" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-handler-proxy" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-resolver" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-resolver-dns" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-transport" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-transport-classes-epoll" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-transport-native-epoll" % V.netty""".raw,
      "dependencyOverrides" in SettingScope.Build += s""""io.netty" % "netty-transport-native-unix-common" % V.netty""".raw,
    ) ++ {
      // override circe version to fix `io.circe:circe-core_2.13:0.14.4 (early-semver) is selected over {0.14.2, 0.13.0}`
      circe.flatMap(
        circe_artifact =>
          Seq(
            "dependencyOverrides" in SettingScope.Build += s""""${circe_artifact.group}" %% "${circe_artifact.artifact}" % ${TkDeps.V.circe.value}""".raw,
            "dependencyOverrides" in SettingScope.Build += s""""${circe_artifact.group}" %% "${circe_artifact.artifact}_sjs1" % ${TkDeps.V.circe.value}""".raw,
          )
      )
    }

    final val sharedSettings = crossScalaSources ++ Seq(
      "scalacOptions" in SettingScope.Compile += """s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}"""".raw,
      "scalacOptions" in SettingScope.Compile += """s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}"""".raw,
      "scalacOptions" in SettingScope.Test += """s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}"""".raw,
      "testOptions" in SettingScope.Test += """Tests.Argument("-oDF")""".raw,
      "logBuffered" in SettingScope.Test := true,
      "testOptions" in SettingScope.Test += """Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit")""".raw,
    )
  }

  final lazy val tk_artifacts = Seq(
    Artifact(
      name = lib.tkMetrics,
      libs = Seq(
        distage_framework,
        zio_core,
      ).map(_ in Scope.Compile.all) ++
        Seq(scala_reflect) ++
        circe.map(_ in Scope.Compile.all) ++
        circe_scala2.map(_ in Scope.Compile.all.scalaVersion(ScalaVersionScope.AllScala2)) ++
        Seq(
          scalatest,
          scalatestplus_scalacheck,
        ).map(_ in Scope.Test.all),
      depends = Seq.empty,
    ),
    Artifact(
      name    = lib.tkMetricsApi,
      libs    = Seq(distage_plugins, distage_framework, idealingua_v1_runtime_rpc_http4s).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkMetrics,
        lib.tkImplicits,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name = lib.tkUtil,
      libs = magnolia_all ++ Seq(
        distage_plugins,
        distage_framework,
        zio_core,
      ).map(_ in Scope.Compile.all),
      depends   = Seq(lib.tkTest in Scope.Test.jvm),
      platforms = TkTargets.crossJs,
    ),
    Artifact(
      name = lib.tkOrc,
      libs = (orc ++ Seq(
        fundamentals_bio,
        distage_framework,
        zio_core,
        fs2_io,
        hadoop_common,
        hadoop_client_runtime,
        hadoop_mapreduce_client,
        hadoop_hive_storage_api,
        protobuf,
      )).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkImplicits,
        lib.tkUtil,
      ).map(_ in Scope.Compile.all) ++ Seq(
        lib.tkTest
      ).map(_ in Scope.Test.all),
    ),
    Artifact(
      name = lib.tkParquet,
      libs = (parquet ++ Seq(
        fundamentals_bio,
        distage_framework,
        zio_core,
        fs2_io,
        hadoop_common,
        hadoop_client_runtime,
        hadoop_mapreduce_client,
      )).map(_ in Scope.Compile.all),
      depends = Seq(lib.tkUtil).map(_ in Scope.Compile.all),
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
        idealingua_v1_runtime_rpc_scala,
        idealingua_v1_runtime_rpc_http4s,
        distage_framework,
        logstage_rendering_circe,
      ).map(_ in Scope.Compile.all) ++ jwt_scala,
      depends = Seq(
        lib.tkMetricsApi,
        lib.tkImplicits,
        lib.tkUtil,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkHttpWs,
      libs    = Seq(asynchttpclient).map(_ in Scope.Compile.all),
      depends = Seq(lib.tkHttpCore, lib.tkHttpWsModels).map(_ in Scope.Compile.all) ++ Seq(lib.tkTest).map(_ in Scope.Test.all),
    ),
    Artifact(
      name     = lib.tkHttpWsModels,
      libs     = Seq.empty,
      depends  = Seq.empty,
      settings = Seq(
        "PB.protocVersion" in SettingScope.Project := "V.protobuf".raw,
        "PB.targets" in SettingScope.Compile := Seq(
          """PB.gens.java(V.protobuf) -> (Compile / sourceManaged).value""".raw
        ),
      ),
      plugins = Plugins(
        enabled = Seq(Plugin("ProtocPlugin"))
      ),
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
      ).map(_ in Scope.Compile.all),
      depends   = Seq.empty,
      platforms = TkTargets.crossJs,
    ),
    Artifact(
      name    = lib.tkCompression,
      libs    = Seq(apache_commons_compress, apache_commons_io, zstd).map(_ in Scope.Compile.all),
      depends = Seq(lib.tkImplicits),
    ),
    Artifact(
      name = lib.tkAws,
      libs = Seq(
        aws_sts,
        aws_netty,
        distage_framework,
      ),
      depends = Seq(lib.tkDocker),
    ),
    Artifact(
      name    = lib.tkAwsS3,
      libs    = Seq(aws_s3, distage_config).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkAws,
        lib.tkUtil,
        lib.tkMetricsApi,
        lib.tkHealth,
        lib.tkImplicits,
        lib.tkTest,
      ),
    ),
    Artifact(
      name    = lib.tkAwsSts,
      libs    = Seq(),
      depends = Seq(
        lib.tkAws,
        lib.tkMetricsApi,
        lib.tkTest,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkAwsSes,
      libs    = Seq(aws_ses, distage_config),
      depends = Seq(
        lib.tkAws,
        lib.tkUtil,
        lib.tkHealth,
        lib.tkImplicits,
        lib.tkTest,
      ),
    ),
    Artifact(
      name    = lib.tkAwsSqs,
      libs    = (Seq(aws_sqs, fs2_io) ++ cats_all).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkAws,
        lib.tkUtil,
        lib.tkHealth,
        lib.tkImplicits,
        lib.tkMetricsApi,
        lib.tkTest,
      ),
    ),
    Artifact(
      name    = lib.tkAwsSns,
      libs    = (Seq(aws_sns) ++ cats_all).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkAws,
        lib.tkHealth,
        lib.tkMetricsApi,
        lib.tkTest,
        lib.tkAwsSqs,
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
      name    = lib.tkAuthTools,
      libs    = Seq(auth0_jwks, tfa),
      depends = Seq(
        lib.tkHttpCore,
        lib.tkImplicits,
        lib.tkCacheGuava,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkLoadtool,
      libs    = Seq(distage_framework).map(_ in Scope.Compile.all),
      depends = Seq(lib.tkUtil, lib.tkImplicits).map(_ in Scope.Compile.all) ++ Seq(lib.tkTest in Scope.Test.all),
    ),
    Artifact(
      name = lib.tkTest,
      libs = magnolia_all ++ (Seq(
        distage_framework,
        distage_testkit,
        fundamentals_platform,
        distage_plugins,
        distage_config,
        scalatest,
        scalatestplus_scalacheck,
        scalacheck,
        log4j_to_slf4j,
      ) ++ distage_docker).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkImplicits
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkDocker,
      libs    = (Seq(distage_framework) ++ distage_docker).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkImplicits,
        lib.tkUtil,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name = lib.tkKafka,
      libs = (Seq(fs2_io) ++ kafka_client ++ circe ++ cats_all).map(_ in Scope.Compile.all) ++
        circe_scala2.map(_ in Scope.Compile.all.scalaVersion(ScalaVersionScope.AllScala2)),
      depends = Seq(
        lib.tkDocker,
        lib.tkMetrics,
        lib.tkHealth,
        lib.tkTest,
      ),
    ),
    Artifact(
      name    = lib.tkZookeeper,
      libs    = (Seq(distage_framework) ++ curator).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkUtil,
        lib.tkImplicits,
        lib.tkTest,
        lib.tkDocker,
        lib.tkHealth,
      ),
    ),
    Artifact(
      name    = lib.tkCacheGuava,
      libs    = Seq(guava),
      depends = Seq(
        lib.tkImplicits,
        lib.tkUtil,
      ).map(_ in Scope.Compile.all) ++ Seq(lib.tkTest in Scope.Test.all),
    ),
    Artifact(
      name    = lib.tkRedis,
      libs    = Seq(redisson, aws_core, aws_auth).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkImplicits,
        lib.tkMetricsApi,
        lib.tkDocker,
        lib.tkTest,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkHealth,
      libs    = Seq.empty,
      depends = Seq(
        lib.tkImplicits
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name = lib.d4s,
      libs = Seq(
        aws_dynamo,
        aws_impl_apache,
      ).map(_ in Scope.Compile.all) ++
        Seq(scala_reflect),
      depends = Seq(
        lib.tkAws,
        lib.tkHealth,
        lib.tkImplicits,
        lib.tkDocker,
        lib.tkMetrics,
        lib.tkUtil,
      ).map(_ in Scope.Compile.all) ++ Seq(lib.tkTest in Scope.Test.all),
    ),
    Artifact(
      name = lib.d4sTest,
      libs = circe.map(_ in Scope.Test.all) ++
        circe_scala2.map(_ in Scope.Test.all.scalaVersion(ScalaVersionScope.AllScala2)),
      depends = Seq(
        lib.d4s,
        lib.tkTest,
        lib.tkImplicits,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name = lib.d4sCirce,
      libs = circe.map(_ in Scope.Compile.all) ++
        circe_scala2.map(_ in Scope.Compile.all.scalaVersion(ScalaVersionScope.AllScala2)),
      depends = Seq(
        lib.d4s in Scope.Compile.all,
        lib.tkTest in Scope.Test.all,
      ),
    ),
    Artifact(
      name    = lib.tkRocksDB,
      libs    = (rocksdb ++ Seq(distage_framework)).map(_ in Scope.Compile.all),
      depends = Seq(
        lib.tkTest,
        lib.tkImplicits,
        lib.tkDocker,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkSynchronizer,
      libs    = Seq.empty,
      depends = Seq(
        lib.tkImplicits
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkSynchronizerZk,
      libs    = Seq.empty,
      depends = Seq(
        lib.tkSynchronizer,
        lib.tkZookeeper,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkSynchronizerPostgres,
      libs    = Seq.empty,
      depends = Seq(
        lib.tkSynchronizer,
        lib.tkPostgres,
      ).map(_ in Scope.Compile.all),
    ),
    Artifact(
      name    = lib.tkNpmResolver,
      libs    = Seq.empty,
      depends = Seq(
        lib.tkHttpCore,
        lib.tkImplicits,
        lib.tkCompression,
        lib.tkTest,
      ),
    ),
  ).map(_.copy(pathPrefix = lib.basePath))

  final lazy val tk_root_agg = Aggregate(
    name             = ArtifactId("playq-tk-root"),
    artifacts        = tk_artifacts,
    groups           = TkGroups.tk,
    defaultPlatforms = TkTargets.jvm,
    sharedSettings   = Seq(
      "scalacOptions" in SettingScope.Compile += "-Xmacro-settings:metricsRole=default"
    ),
  )

  final val tk_project = Project(
    name       = ArtifactId("playq-tk"),
    aggregates = {
      Seq(tk_root_agg)
    },
    sharedSettings    = ProjectSettings.sharedSettings,
    sharedAggSettings = Seq(
      "crossScalaVersions" := "Nil".raw
    ),
    rootSettings = ProjectSettings.rootProjectSettings,
    imports      = ProjectSettings.sharedImports,
    globalLibs   = Seq(
      ScopedLibrary(projector, FullDependencyScope(Scope.Compile, Platform.All, ScalaVersionScope.AllScala2), compilerPlugin = true)
    ),
    rootPlugins = Plugins(
      enabled = Seq(
        Plugin("IzumiPublishingPlugin"),
        Plugin("IzumiResolverPlugin"),
        Plugin("IzumiConvenienceTasksPlugin"),
        Plugin("SbtgenVerificationPlugin"),
      )
    ),
    globalPlugins = Plugins(
      enabled = Seq(
        Plugin("IzumiPublishingPlugin"),
        Plugin("IzumiResolverPlugin"),
      )
    ),
    appendPlugins = Defaults.SbtGenPlugins ++ Seq(
      SbtPlugin("io.7mind.izumi", "sbt-izumi-deps", V.sbt_izumi),
      SbtPlugin("org.scoverage", "sbt-scoverage", V.scoverage),
      SbtPlugin("com.thesamet", "sbt-protoc", V.sbt_protoc),
    ),
  )
}
