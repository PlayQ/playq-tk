package tk.sbtgen

import izumi.sbtgen.model.LibSetting.Exclusion
import izumi.sbtgen.model._

object TkDeps {
  object V {
    val sbt_izumi  = Version.VExpr("Tk.PV.izumi_version")
    val scoverage  = Version.VExpr("Tk.PV.scoverage_version")
    val sbt_protoc = Version.VExpr("Tk.PV.sbt_protoc")
    val izumi      = Version.VExpr("Izumi.version")
    val idealingua = Version.VExpr("V.izumi_idealingua_version")

    val cats        = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_core_version")
    val cats_effect = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.org_typelevel_cats_effect_version")

    val zio              = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.dev_zio_zio_version")
    val zio_interop_cats = Version.VExpr("Izumi.Deps.fundamentals_bioJVM.dev_zio_zio_interop_cats_version")

    val circe                = Version.VExpr("Izumi.Deps.fundamentals_json_circeJVM.io_circe_circe_core_version")
    val circe_derivation     = Version.VExpr("V.circe_derivation")
    val circe_generic_extras = Version.VExpr("V.circe_generic_extras")

    val doobie = Version.VExpr("V.doobie")

    val aws_sdk = Version.VExpr("V.aws_java_sdk")

    val scalatest                = Version.VExpr("Izumi.Deps.distage_testkit_scalatest.org_scalatest_scalatest_version")
    val scalatestplus_scalacheck = Version.VExpr("V.scalatestplus_scalacheck")
  }

  final val distage_core          = Library("io.7mind.izumi", "distage-core", V.izumi, LibraryType.Auto)
  final val distage_framework     = Library("io.7mind.izumi", "distage-framework", V.izumi, LibraryType.Auto)
  final val distage_plugins       = Library("io.7mind.izumi", "distage-extension-plugins", V.izumi, LibraryType.Auto)
  final val distage_config        = Library("io.7mind.izumi", "distage-extension-config", V.izumi, LibraryType.Auto)
  final val distage_framework_api = Library("io.7mind.izumi", "distage-framework-api", V.izumi, LibraryType.Auto)
  final val distage_testkit       = Library("io.7mind.izumi", "distage-testkit-scalatest", V.izumi, LibraryType.Auto)
  final val distage_docker        = {
    Seq(
      Library("io.7mind.izumi", "distage-framework-docker", V.izumi, LibraryType.Auto).exclude("com.google.guava", "guava"),
      Library("com.google.guava", "guava", Version.VExpr("V.guava"), LibraryType.Invariant),
    )
  }

  final val idealingua_v1_runtime_rpc_http4s = Library("io.7mind.izumi", "idealingua-v1-runtime-rpc-http4s", V.idealingua, LibraryType.Auto)
  final val idealingua_v1_runtime_rpc_scala  = Library("io.7mind.izumi", "idealingua-v1-runtime-rpc-scala", V.idealingua, LibraryType.Auto)
  final val idealingua_v1_model              = Library("io.7mind.izumi", "idealingua-v1-model", V.idealingua, LibraryType.Auto)

  final val circe_core = Library("io.circe", "circe-core", V.circe, LibraryType.Auto)

  final val circe = Seq(
    circe_core,
    Library("io.circe", "circe-generic", V.circe, LibraryType.Auto),
    Library("io.circe", "circe-parser", V.circe, LibraryType.Auto),
    Library("io.circe", "circe-literal", V.circe, LibraryType.Auto),
    Library("io.circe", "circe-pointer", V.circe, LibraryType.Auto),
    Library("io.circe", "circe-pointer-literal", V.circe, LibraryType.Auto),
  )

  final val circe_scala2 = Seq(
    Library("io.circe", "circe-generic-extras", V.circe_generic_extras, LibraryType.Auto),
    Library("io.circe", "circe-derivation", V.circe_derivation, LibraryType.Auto),
  )

  final val scala_reflect = Library("org.scala-lang", "scala-reflect", Version.VExpr("scalaVersion.value"), LibraryType.Invariant) in Scope.Provided.all.scalaVersion(
    ScalaVersionScope.AllScala2
  )

  final val logstage_rendering_circe = Library("io.7mind.izumi", "logstage-rendering-circe", V.izumi, LibraryType.Auto)
  final val logstage_adapter_slf4j   = Library("io.7mind.izumi", "logstage-adapter-slf4j", V.izumi, LibraryType.Auto)
  final val logstage_core            = Library("io.7mind.izumi", "logstage-core", V.izumi, LibraryType.Auto)

  final val cats_core   = Library("org.typelevel", "cats-core", V.cats, LibraryType.Auto)
  final val cats_effect = Library("org.typelevel", "cats-effect", V.cats_effect, LibraryType.Auto)
  final val cats_all    = Seq(cats_core, cats_effect)
  final val zio_managed = Library("dev.zio", "zio-managed", V.zio, LibraryType.Auto)
  final val zio_core    = Library("dev.zio", "zio", V.zio, LibraryType.Auto)
  final val zio_interop = Library("dev.zio", "zio-interop-cats", V.zio_interop_cats, LibraryType.Auto)

  final val scalatest                = Library("org.scalatest", "scalatest", V.scalatest, LibraryType.Auto)
  final val scalatestplus_scalacheck = Library("org.scalatestplus", "scalacheck-1-16", V.scalatestplus_scalacheck, LibraryType.Auto)
  final val fundamentals_bio         = Library("io.7mind.izumi", "fundamentals-bio", V.izumi, LibraryType.Auto)
  final val fundamentals_platform    = Library("io.7mind.izumi", "fundamentals-platform", V.izumi, LibraryType.Auto)
  final val fundamentals_collections = Library("io.7mind.izumi", "fundamentals-collections", V.izumi, LibraryType.Auto)
  final val fundamentals_functional  = Library("io.7mind.izumi", "fundamentals-functional", V.izumi, LibraryType.Auto)
  final val fundamentals_json_circe  = Library("io.7mind.izumi", "fundamentals-json-circe", V.izumi, LibraryType.Auto)

  final val projector = Library("org.typelevel", "kind-projector", Version.VExpr("V.kind_projector"), LibraryType.Invariant)
    .more(LibSetting.Raw("""cross CrossVersion.full"""))

  final val fs2_io       = Library("co.fs2", "fs2-io", Version.VExpr("V.fs2"), LibraryType.Auto)
  final val magnolia_all = Seq(
    Library("com.softwaremill.magnolia1_2", "magnolia", Version.VExpr("V.magnolia"), LibraryType.Auto) in Scope.Compile.all.scalaVersion(ScalaVersionScope.AllScala2),
    Library("com.softwaremill.magnolia1_3", "magnolia", Version.VExpr("V.magnolia_3"), LibraryType.Auto) in Scope.Compile.all.scalaVersion(ScalaVersionScope.AllScala3),
  )

  final val doobie_core      = Library("org.tpolecat", "doobie-core", V.doobie, LibraryType.Auto)
  final val doobie_postgres  = Library("org.tpolecat", "doobie-postgres", V.doobie, LibraryType.Auto)
  final val doobie_circe     = Library("org.tpolecat", "doobie-postgres-circe", V.doobie, LibraryType.Auto) // unused, remove?
  final val doobie_hikari    = Library("org.tpolecat", "doobie-hikari", V.doobie, LibraryType.Auto)
  final val doobie_scalatest = Library("org.tpolecat", "doobie-scalatest", V.doobie, LibraryType.Auto) // unused, remove?
  final val doobie_all       = Seq(doobie_core, doobie_hikari, doobie_postgres, doobie_circe)

  final val jwt_scala = Seq(
    Library("com.github.jwt-scala", "jwt-circe", Version.VExpr("V.jwt_scala"), LibraryType.Auto),
    Library("org.bouncycastle", "bcpkix-jdk18on", Version.VExpr("V.bouncycastle"), LibraryType.Invariant),
  ).map(_ in Scope.Compile.all)

  final val scalacheck = Library("org.scalacheck", "scalacheck", Version.VExpr("V.scalacheck"), LibraryType.Auto)

  final val zstd = Library("com.github.luben", "zstd-jni", Version.VExpr("V.zstd"), LibraryType.Invariant)

  final val kafka_client = Seq(
    zstd,
    Library("org.apache.kafka", "kafka-clients", Version.VExpr("V.kafka"), LibraryType.Invariant)
      .more(
        LibSetting.Exclusions(
          Seq(
            Exclusion("org.slf4j", "log4j-over-slf4j"),
            Exclusion("javax.jms", "jms"),
            Exclusion("com.sun.jdmk", "jmxtools"),
            Exclusion("com.sun.jmx", "jmxri"),
            Exclusion("log4j", "log4j"),
            // exclude zstd to resolve conflicts
            Exclusion("com.github.luben", "zstd-jni"),
          )
        )
      ),
  )

  final val aws_auth = Library("software.amazon.awssdk", "auth", V.aws_sdk, LibraryType.Invariant)
    .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

  final val aws_netty = Library("software.amazon.awssdk", "netty-nio-client", V.aws_sdk, LibraryType.Invariant)
    .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

  final val aws_core = Library("software.amazon.awssdk", "sdk-core", V.aws_sdk, LibraryType.Invariant)
    .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

  final val aws_s3 = Library("software.amazon.awssdk", "s3", V.aws_sdk, LibraryType.Invariant)
    .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

  final val aws_dynamo = Library("software.amazon.awssdk", "dynamodb", V.aws_sdk, LibraryType.Invariant)
    .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

  final val aws_impl_apache = Library("software.amazon.awssdk", "apache-client", V.aws_sdk, LibraryType.Invariant)
    .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

  final val aws_sqs = Library("software.amazon.awssdk", "sqs", V.aws_sdk, LibraryType.Invariant)
    .more(LibSetting.Exclusions(Seq(Exclusion("log4j", "log4j"))))

  final val aws_sns = Library("software.amazon.awssdk", "sns", V.aws_sdk, LibraryType.Invariant)
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
  )
  final val redisson = Library("org.redisson", "redisson", Version.VExpr("V.redisson"), LibraryType.Invariant)
  final val guava    = Library("com.google.guava", "guava", Version.VExpr("V.guava"), LibraryType.Invariant)

  final val auth0_jwks = Library("com.auth0", "jwks-rsa", Version.VExpr("V.auth0_jwks"), LibraryType.Invariant)
  final val tfa        = Library("com.j256.two-factor-auth", "two-factor-auth", Version.VExpr("V.tfa"), LibraryType.Invariant)

  final val log4j_to_slf4j = Library("org.apache.logging.log4j", "log4j-to-slf4j", Version.VExpr("V.log4j_to_slf4j"), LibraryType.Invariant)
  final val protobuf       = Library("com.google.protobuf", "protobuf-java", Version.VExpr("V.protobuf"), LibraryType.Invariant)
  final val rocksdb        = Seq(
    Library("org.rocksdb", "rocksdbjni", Version.VExpr("V.rocksdb"), LibraryType.Invariant),
    Library("org.rocksdb", "rocksdbjni", Version.VExpr("V.rocksdb"), LibraryType.Invariant).classifier("linux64").classifier("osx"),
  )

  final val orc = Seq(
    zstd,
    Library("org.apache.orc", "orc-core", Version.VExpr("V.orc"), LibraryType.Invariant).more(
      LibSetting.Exclusions(
        Seq(
          Exclusion("org.slf4j", "slf4j-log4j12"),
          Exclusion("org.slf4j", "slf4j-reload4j"),
          Exclusion("org.apache.logging.log4j", "log4j-slf4j-impl"),
          Exclusion("org.apache.zookeeper", "zookeeper"),
          Exclusion("org.apache.hadoop", "hadoop-common"),
          Exclusion("org.apache.hadoop", "hadoop-hdfs"),
          Exclusion("org.apache.curator", "curator-client"),
          Exclusion("org.apache.curator", "curator-framework"),
          Exclusion("org.apache.curator", "curator-recipes"),
          Exclusion("io.netty", "netty-all"),
          Exclusion("io.netty", "netty"),
          Exclusion("log4j", "log4j"),
          // exclude zstd, to resolve version conflicts
          Exclusion("com.github.luben", "zstd-jni"),
        )
      )
    ),
  )

  // parquet reader dependency
  final val hadoop_mapreduce_client = Library("org.apache.hadoop", "hadoop-mapreduce-client-core", Version.VExpr("V.hadoop_common"), LibraryType.Invariant)
    .more(
      LibSetting.Exclusions(
        Seq(
          Exclusion("org.slf4j", "slf4j-log4j12"),
          Exclusion("org.slf4j", "slf4j-reload4j"),
          Exclusion("org.apache.logging.log4j", "log4j-slf4j-impl"),
          Exclusion("org.apache.zookeeper", "zookeeper"),
          Exclusion("org.apache.curator", "curator-client"),
          Exclusion("org.apache.curator", "curator-framework"),
          Exclusion("org.apache.curator", "curator-recipes"),
          Exclusion("io.netty", "netty-all"),
          Exclusion("io.netty", "netty"),
          Exclusion("log4j", "log4j"),
        )
      )
    )
  final val parquet = Seq(
    zstd,
    Library("org.apache.parquet", "parquet-avro", Version.VExpr("V.parquet"), LibraryType.Invariant).more(
      LibSetting.Exclusions(
        Seq(
          Exclusion("org.slf4j", "slf4j-log4j12"),
          Exclusion("org.slf4j", "slf4j-reload4j"),
          Exclusion("org.apache.logging.log4j", "log4j-slf4j-impl"),
          Exclusion("org.apache.zookeeper", "zookeeper"),
          Exclusion("org.apache.hadoop", "hadoop-common"),
          Exclusion("org.apache.hadoop", "hadoop-hdfs"),
          Exclusion("org.apache.curator", "curator-client"),
          Exclusion("org.apache.curator", "curator-framework"),
          Exclusion("org.apache.curator", "curator-recipes"),
          Exclusion("io.netty", "netty-all"),
          Exclusion("io.netty", "netty"),
          Exclusion("log4j", "log4j"),
          // exclude zstd, to resolve version conflicts
          Exclusion("com.github.luben", "zstd-jni"),
        )
      )
    ),
  )

  // orc dependency
  final val hadoop_hive_storage_api = Library("org.apache.hive", "hive-storage-api", Version.VExpr("V.hadoop_hive_storage_api"), LibraryType.Invariant)
    .more(
      LibSetting.Exclusions(
        Seq(
          Exclusion("org.slf4j", "slf4j-log4j12"),
          Exclusion("org.slf4j", "slf4j-reload4j"),
          Exclusion("org.apache.hadoop", "hadoop-common"),
          Exclusion("org.apache.hadoop", "hadoop-hdfs"),
          Exclusion("org.apache.zookeeper", "zookeeper"),
          Exclusion("org.apache.curator", "curator-client"),
          Exclusion("org.apache.curator", "curator-framework"),
          Exclusion("org.apache.curator", "curator-recipes"),
          Exclusion("io.netty", "netty-all"),
          Exclusion("io.netty", "netty"),
          Exclusion("log4j", "log4j"),
        )
      )
    )
  // parquet dependency
  final val hadoop_common = Library("org.apache.hadoop", "hadoop-common", Version.VExpr("V.hadoop_common"), LibraryType.Invariant) // parquet writer dependency
    .more(
      LibSetting.Exclusions(
        Seq(
          Exclusion("org.slf4j", "slf4j-log4j12"),
          Exclusion("org.slf4j", "slf4j-reload4j"),
          Exclusion("org.apache.zookeeper", "zookeeper"),
          Exclusion("org.apache.curator", "curator-client"),
          Exclusion("org.apache.curator", "curator-framework"),
          Exclusion("org.apache.curator", "curator-recipes"),
          Exclusion("io.netty", "netty-all"),
          Exclusion("io.netty", "netty"),
          Exclusion("log4j", "log4j"),
        )
      )
    )
  // parquet dependency
  final val hadoop_client_runtime =
    Library("org.apache.hadoop", "hadoop-client-runtime", Version.VExpr("V.hadoop_common"), LibraryType.Invariant) // parquet writer dependency
      .more(
        LibSetting.Exclusions(
          Seq(
            Exclusion("org.slf4j", "slf4j-log4j12"),
            Exclusion("org.slf4j", "slf4j-reload4j"),
            Exclusion("org.apache.zookeeper", "zookeeper"),
            Exclusion("org.apache.curator", "curator-client"),
            Exclusion("org.apache.curator", "curator-framework"),
            Exclusion("org.apache.curator", "curator-recipes"),
            Exclusion("io.netty", "netty-all"),
            Exclusion("io.netty", "netty"),
            Exclusion("log4j", "log4j"),
          )
        )
      )

  final val asynchttpclient = Library("org.asynchttpclient", "async-http-client", Version.VExpr("V.asynchttpclient"), LibraryType.Invariant)

  final val apache_commons_compress = Library("org.apache.commons", "commons-compress", Version.VExpr("V.apache_commons_compress"), LibraryType.Invariant)
  final val apache_commons_io       = Library("commons-io", "commons-io", Version.VExpr("V.apache_commons_io"), LibraryType.Invariant)
}
