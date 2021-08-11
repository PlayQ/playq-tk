import IzumiPublishingPlugin.Keys._
import IzumiPublishingPlugin.autoImport._
import IzumiConvenienceTasksPlugin.Keys._
import sbtrelease.ReleaseStateTransformations._

enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin, IzumiConvenienceTasksPlugin, SbtgenVerificationPlugin)

lazy val `tk-metrics` = project.in(file("./tk-metrics"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      "io.circe" %% "circe-core" % V.circe % Optional,
      "io.circe" %% "circe-generic" % V.circe % Optional,
      "io.circe" %% "circe-generic-extras" % V.circe_generic_extras % Optional,
      "io.circe" %% "circe-parser" % V.circe % Optional,
      "io.circe" %% "circe-literal" % V.circe % Optional,
      "io.circe" %% "circe-derivation" % V.circe_derivation % Optional,
      "io.circe" %% "circe-pointer" % V.circe_pointer % Optional,
      "io.circe" %% "circe-pointer-literal" % V.circe_pointer % Optional,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.scalatestplus" %% "scalacheck-1-15" % V.scalatestplus_scalacheck % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % V.scalacheck_shapeless % Test
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-metrics-api` = project.in(file("./tk-metrics-api"))
  .dependsOn(
    `tk-metrics` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-extension-plugins" % V.izumi_version,
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "io.7mind.izumi" %% "idealingua-v1-runtime-rpc-http4s" % V.izumi_idealingua_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-util` = project.in(file("./tk-util"))
  .dependsOn(
    `tk-test` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-extension-plugins" % V.izumi_version,
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "dev.zio" %% "zio" % Izumi.Deps.fundamentals_bioJVM.dev_zio_zio_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-postgres` = project.in(file("./tk-postgres"))
  .dependsOn(
    `tk-health` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "org.tpolecat" %% "doobie-scalatest" % V.doobie % Test,
      "org.tpolecat" %% "doobie-core" % V.doobie,
      "org.tpolecat" %% "doobie-hikari" % V.doobie,
      "org.tpolecat" %% "doobie-postgres" % V.doobie,
      "org.tpolecat" %% "doobie-postgres-circe" % V.doobie,
      "io.7mind.izumi" %% "distage-extension-plugins" % V.izumi_version,
      "io.7mind.izumi" %% "distage-extension-config" % V.izumi_version,
      "io.7mind.izumi" %% "distage-framework-api" % V.izumi_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-launcher-core` = project.in(file("./tk-launcher-core"))
  .dependsOn(
    `tk-implicits` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "io.7mind.izumi" %% "logstage-rendering-circe" % V.izumi_version,
      "io.7mind.izumi" %% "logstage-adapter-slf4j" % V.izumi_version,
      "org.apache.logging.log4j" % "log4j-to-slf4j" % V.log4j_to_slf4j
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-http-core` = project.in(file("./tk-http-core"))
  .dependsOn(
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "io.7mind.izumi" %% "logstage-rendering-circe" % V.izumi_version,
      "io.7mind.izumi" %% "idealingua-v1-runtime-rpc-scala" % V.izumi_idealingua_version,
      "io.7mind.izumi" %% "idealingua-v1-runtime-rpc-http4s" % V.izumi_idealingua_version,
      "com.pauldijou" %% "jwt-circe" % V.jwt_scala,
      "org.bouncycastle" % "bcpkix-jdk15on" % V.bouncycastle
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-implicits` = project.in(file("./tk-implicits"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-extension-plugins" % V.izumi_version,
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "org.typelevel" %% "cats-effect" % Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_effect_version,
      "org.typelevel" %% "cats-core" % Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_core_version,
      "dev.zio" %% "zio-interop-cats" % Izumi.Deps.fundamentals_bioJVM.dev_zio_zio_interop_cats_version,
      "dev.zio" %% "zio" % Izumi.Deps.fundamentals_bioJVM.dev_zio_zio_version,
      "io.7mind.izumi" %% "logstage-core" % V.izumi_version,
      "co.fs2" %% "fs2-io" % V.fs2,
      "com.propensive" %% "magnolia" % V.magnolia
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws` = project.in(file("./tk-aws"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "sts" % V.aws_java_sdk exclude ("log4j", "log4j"),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws-s3` = project.in(file("./tk-aws-s3"))
  .dependsOn(
    `tk-aws` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-health` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "s3" % V.aws_java_sdk exclude ("log4j", "log4j"),
      "io.7mind.izumi" %% "distage-extension-config" % V.izumi_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws-sts` = project.in(file("./tk-aws-sts"))
  .dependsOn(
    `tk-aws` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5"))
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws-ses` = project.in(file("./tk-aws-ses"))
  .dependsOn(
    `tk-aws` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile",
    `tk-health` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "ses" % V.aws_java_sdk exclude ("log4j", "log4j"),
      "io.7mind.izumi" %% "distage-extension-config" % V.izumi_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws-sqs` = project.in(file("./tk-aws-sqs"))
  .dependsOn(
    `tk-aws` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile",
    `tk-health` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "sqs" % V.aws_java_sdk exclude ("log4j", "log4j"),
      "co.fs2" %% "fs2-io" % V.fs2,
      "org.typelevel" %% "cats-core" % Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_core_version,
      "org.typelevel" %% "cats-effect" % Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_effect_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws-cost` = project.in(file("./tk-aws-cost"))
  .dependsOn(
    `tk-aws` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "costexplorer" % V.aws_java_sdk exclude ("log4j", "log4j")
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws-lambda` = project.in(file("./tk-aws-lambda"))
  .dependsOn(
    `tk-aws` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "lambda" % V.aws_java_sdk exclude ("log4j", "log4j")
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-aws-sagemaker` = project.in(file("./tk-aws-sagemaker"))
  .dependsOn(
    `tk-aws-s3` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "sagemaker" % V.aws_java_sdk exclude ("log4j", "log4j")
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-auth-tools` = project.in(file("./tk-auth-tools"))
  .dependsOn(
    `tk-http-core` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "com.auth0" % "jwks-rsa" % V.auth0_jwks,
      "com.j256.two-factor-auth" % "two-factor-auth" % V.tfa
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-loadtool` = project.in(file("./tk-loadtool"))
  .dependsOn(
    `tk-util` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-test` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-test` = project.in(file("./tk-test"))
  .dependsOn(
    `tk-implicits` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "io.7mind.izumi" %% "distage-testkit-scalatest" % V.izumi_version,
      "io.7mind.izumi" %% "fundamentals-platform" % V.izumi_version,
      "io.7mind.izumi" %% "distage-framework-docker" % V.izumi_version,
      "io.7mind.izumi" %% "distage-extension-plugins" % V.izumi_version,
      "io.7mind.izumi" %% "distage-extension-config" % V.izumi_version,
      "org.scalatest" %% "scalatest" % V.scalatest,
      "org.scalatestplus" %% "scalacheck-1-15" % V.scalatestplus_scalacheck,
      "org.scalacheck" %% "scalacheck" % V.scalacheck,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % V.scalacheck_shapeless,
      "org.apache.logging.log4j" % "log4j-to-slf4j" % V.log4j_to_slf4j
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-docker` = project.in(file("./tk-docker"))
  .dependsOn(
    `tk-implicits` % "test->compile;compile->compile",
    `tk-util` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "io.7mind.izumi" %% "distage-framework-docker" % V.izumi_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `fs2-kafka-client` = project.in(file("./fs2-kafka-client"))
  .dependsOn(
    `tk-implicits` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "org.apache.kafka" % "kafka-clients" % V.kafka exclude ("org.slf4j", "log4j-over-slf4j") exclude ("javax.jms", "jms") exclude ("com.sun.jdmk", "jmxtools") exclude ("com.sun.jmx", "jmxri") exclude ("log4j", "log4j"),
      "co.fs2" %% "fs2-io" % V.fs2,
      "org.typelevel" %% "cats-core" % Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_core_version,
      "org.typelevel" %% "cats-effect" % Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_effect_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-kafka` = project.in(file("./tk-kafka"))
  .dependsOn(
    `fs2-kafka-client` % "test->compile;compile->compile",
    `tk-zookeeper` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile",
    `tk-metrics` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "org.apache.kafka" % "kafka-clients" % V.kafka exclude ("org.slf4j", "log4j-over-slf4j") exclude ("javax.jms", "jms") exclude ("com.sun.jdmk", "jmxtools") exclude ("com.sun.jmx", "jmxri") exclude ("log4j", "log4j")
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-zookeeper` = project.in(file("./tk-zookeeper"))
  .dependsOn(
    `tk-util` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version,
      "org.apache.curator" % "curator-recipes" % V.curator exclude ("org.apache.zookeeper", "zookeeper"),
      "org.apache.zookeeper" % "zookeeper" % V.zookeeper exclude ("org.slf4j", "slf4j-log4j12")
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-cache-guava` = project.in(file("./tk-cache-guava"))
  .dependsOn(
    `tk-implicits` % "test->compile;compile->compile",
    `tk-test` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "com.google.guava" % "guava" % V.guava
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-redis` = project.in(file("./tk-redis"))
  .dependsOn(
    `tk-implicits` % "test->compile;compile->compile",
    `tk-metrics-api` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "redis.clients" % "jedis" % V.jedis
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-health` = project.in(file("./tk-health"))
  .dependsOn(
    `tk-implicits` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5"))
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `d4s` = project.in(file("./d4s"))
  .dependsOn(
    `tk-aws` % "test->compile;compile->compile",
    `tk-health` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile",
    `tk-metrics` % "test->compile;compile->compile",
    `tk-test` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "software.amazon.awssdk" % "dynamodb" % V.aws_java_sdk exclude ("log4j", "log4j"),
      "software.amazon.awssdk" % "apache-client" % V.aws_java_sdk exclude ("log4j", "log4j"),
      "com.propensive" %% "magnolia" % V.magnolia,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `d4s-test` = project.in(file("./d4s-test"))
  .dependsOn(
    `d4s` % "test->compile;compile->compile",
    `tk-test` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5"))
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `d4s-circe` = project.in(file("./d4s-circe"))
  .dependsOn(
    `d4s` % "test->compile;compile->compile",
    `tk-test` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-generic-extras" % V.circe_generic_extras,
      "io.circe" %% "circe-parser" % V.circe,
      "io.circe" %% "circe-literal" % V.circe,
      "io.circe" %% "circe-derivation" % V.circe_derivation,
      "io.circe" %% "circe-pointer" % V.circe_pointer,
      "io.circe" %% "circe-pointer-literal" % V.circe_pointer
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk-rocksdb` = project.in(file("./tk-rocksdb"))
  .dependsOn(
    `tk-test` % "test->compile;compile->compile",
    `tk-implicits` % "test->compile;compile->compile",
    `tk-docker` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.constant("2.13.5")),
      "org.rocksdb" % "rocksdbjni" % V.rocksdb,
      "io.7mind.izumi" %% "distage-framework" % V.izumi_version
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6"
    ),
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= Seq(
      "-Wconf:msg=package.object.inheritance:silent",
      if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
      "-Wconf:cat=optimizer:warning",
      "-Wconf:cat=other-match-analysis:error",
      "-Vimplicits",
      "-Vtype-diffs",
      "-Ybackend-parallelism",
      math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
      "-Wunused:_",
      "-Wmacros:after",
      "-Ycache-plugin-class-loader:always",
      "-Ycache-macro-class-loader:last-modified"
    ),
    scalacOptions ++= Seq(
      "-Wconf:msg=kind-projector:silent",
      "-Wconf:msg=will.be.interpreted.as.a.wildcard.in.the.future:silent"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, _) => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**",
        "-opt-inline-from:net.playq.**"
      )
      case (_, _) => Seq.empty
    } },
    organization := "net.playq",
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/main/scala" ,
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/main/resources" ,
    Test / unmanagedSourceDirectories += baseDirectory.value / ".jvm/src/test/scala" ,
    Test / unmanagedResourceDirectories += baseDirectory.value / ".jvm/src/test/resources" ,
    Compile / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Compile / classDirectory).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsDir=${(Test / classDirectory).value}",
    Compile / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Compile / name).value};${(Compile / moduleName).value}",
    Test / scalacOptions += s"-Xmacro-settings:metricsRole=${(Test / name).value};${(Test / moduleName).value}",
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", s"${(Compile / target).value.toPath.toAbsolutePath}/test-reports/junit"),
    resolvers += DefaultMavenRepository,
    resolvers += Opts.resolver.sonatypeSnapshots,
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Compile / scalacOptions += "-Xmacro-settings:metricsRole=default"
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)

lazy val `tk` = (project in file(".agg/.-tk"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)
  .aggregate(
    `tk-metrics`,
    `tk-metrics-api`,
    `tk-util`,
    `tk-postgres`,
    `tk-launcher-core`,
    `tk-http-core`,
    `tk-implicits`,
    `tk-aws`,
    `tk-aws-s3`,
    `tk-aws-sts`,
    `tk-aws-ses`,
    `tk-aws-sqs`,
    `tk-aws-cost`,
    `tk-aws-lambda`,
    `tk-aws-sagemaker`,
    `tk-auth-tools`,
    `tk-loadtool`,
    `tk-test`,
    `tk-docker`,
    `fs2-kafka-client`,
    `tk-kafka`,
    `tk-zookeeper`,
    `tk-cache-guava`,
    `tk-redis`,
    `tk-health`,
    `d4s`,
    `d4s-test`,
    `d4s-circe`,
    `tk-rocksdb`
  )

lazy val `tk-jvm` = (project in file(".agg/.-tk-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil
  )
  .aggregate(
    `tk-metrics`,
    `tk-metrics-api`,
    `tk-util`,
    `tk-postgres`,
    `tk-launcher-core`,
    `tk-http-core`,
    `tk-implicits`,
    `tk-aws`,
    `tk-aws-s3`,
    `tk-aws-sts`,
    `tk-aws-ses`,
    `tk-aws-sqs`,
    `tk-aws-cost`,
    `tk-aws-lambda`,
    `tk-aws-sagemaker`,
    `tk-auth-tools`,
    `tk-loadtool`,
    `tk-test`,
    `tk-docker`,
    `fs2-kafka-client`,
    `tk-kafka`,
    `tk-zookeeper`,
    `tk-cache-guava`,
    `tk-redis`,
    `tk-health`,
    `d4s`,
    `d4s-test`,
    `d4s-circe`,
    `tk-rocksdb`
  )

lazy val `playq-tk-jvm` = (project in file(".agg/.agg-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil
  )
  .aggregate(
    `tk-jvm`
  )

lazy val `playq-tk` = (project in file("."))
  .settings(
    publish / skip := true,
    ThisBuild / publishMavenStyle := true,
    ThisBuild / scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-target:jvm-1.8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:higherKinds",
      "-explaintypes"
    ),
    ThisBuild / javacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-source",
      "1.8",
      "-target",
      "1.8",
      "-deprecation",
      "-parameters",
      "-Xlint:all",
      "-XDignore.symbol.file"
    ),
    ThisBuild / scalacOptions ++= Seq(
      s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
      s"-Xmacro-settings:git-repo-clean=${com.typesafe.sbt.SbtGit.GitKeys.gitUncommittedChanges.value}",
      s"-Xmacro-settings:git-branch=${com.typesafe.sbt.SbtGit.GitKeys.gitCurrentBranch.value}",
      s"-Xmacro-settings:git-described-version=${com.typesafe.sbt.SbtGit.GitKeys.gitDescribedVersion.value.getOrElse("")}",
      s"-Xmacro-settings:git-head-commit=${com.typesafe.sbt.SbtGit.GitKeys.gitHeadCommit.value.getOrElse("")}"
    ),
    Global / fork := false,
    crossScalaVersions := Nil,
    scalaVersion := "2.13.6",
    Global / coverageOutputXML := true,
    Global / coverageOutputHTML := true,
    Global / organization := "net.playq",
    sonatypeProfileName := "net.playq",
    sonatypeSessionName := s"[sbt-sonatype] ${name.value} ${version.value} ${java.util.UUID.randomUUID}",
    ThisBuild / publishTo := 
    (if (!isSnapshot.value) {
        sonatypePublishToBundle.value
      } else {
        Some(Opts.resolver.sonatypeSnapshots)
    })
    ,
    ThisBuild / credentials += Credentials(file(".secrets/credentials.sonatype-nexus.properties")),
    ThisBuild / homepage := Some(url("https://www.playq.com/")),
    ThisBuild / licenses := Seq("Apache-License" -> url("https://opensource.org/licenses/Apache-2.0")),
    ThisBuild / developers := List(
              Developer(id = "playq", name = "PlayQ", url = url("https://github.com/PlayQ"), email = "platform-team@playq.net"),
            ),
    ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/PlayQ/playq-tk"), "scm:git:https://github.com/PlayQ/playq-tk.git")),
    ThisBuild / scalacOptions += """-Xmacro-settings:scalatest-version=VExpr(V.scalatest)""",
    ThisBuild / scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=playq-tk",
      s"-Xmacro-settings:product-group=playq-tk",
      s"-Xmacro-settings:product-version=${ThisBuild / version}"
    ),
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      //publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )
  .enablePlugins(IzumiPublishingPlugin, IzumiResolverPlugin)
  .aggregate(
    `tk`
  )
