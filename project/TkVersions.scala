package Tk

object V extends V
trait V {
  val scala_213 = "2.13.18"
  val scala_300 = "3.8.4-RC2"

  // izumi version is set by sbt-izumi-deps plugin
  val izumi_idealingua_version = "1.4.16"

  // ToolKit Deps

  // compiler
  val kind_projector = "0.13.4"
  val scapegoat      = "3.3.4"

  // scala
  val jwt_scala    = "11.0.4"
  val bouncycastle = "1.84"

  val doobie = "1.0.0-RC12"

  val circe_generic_extras = "0.14.4"
  val circe_derivation     = "0.13.0-M5"

  // used by http4s 0.23.33
  val fs2        = "3.12.2"
  val magnolia   = "1.1.13"
  val magnolia_3 = "1.3.18"

  val asynchttpclient = "3.0.9"

  // java
  val protobuf     = "3.25.5"
  val guava        = "33.6.0-jre"
  val opendmk      = "1.0_01-ea"
  val aws_java_sdk = "2.42.39"
  val redisson     = "4.3.1"
  val tfa          = "1.3"
  val auth0_jwks   = "0.23.1"
  val netty        = "4.1.130.Final"

  val zstd = "1.5.7-7"

  val apache_commons_text     = "1.15.0"
  val apache_commons_compress = "1.28.0"
  val apache_commons_io       = "2.21.0"

  val log4j_to_slf4j = "2.25.4"
  val slf4j          = "2.0.17"

  val rocksdb = "10.10.1.1"

  val scalacheck               = "1.19.0"
  val scalacheck_shapeless     = "1.2.5"
  val scalatestplus_scalacheck = "3.2.14.0"

  // latest Kafka version is fully compatible with the old servers
  val kafka = "4.2.0"
  // apache curator 5.5.0+ using ZooKeeper 3.7.1+ version
  // (all versions of ZooKeeper are compatible with 3.5 and newer servers)
  val curator = "5.9.0"

  // hadoop versions (be careful updating hadoop-common, as it may break ZooKeeper)
  val hadoop_hive_storage_api = "4.2.0"
  val hadoop_common           = "3.5.0"
  val parquet                 = "1.17.0"
  val orc                     = "2.3.0"
}
