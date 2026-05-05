package net.playq.tk.redis.config

import cats.data.NonEmptyList
import izumi.distage.config.codec.{ConfigMetaBasicType, ConfigMetaType, DIConfigMeta, DIConfigReader}
import net.playq.tk.redis.config.RedisConfig.{RedisAuth, RedisAuthMode, RedisServerMode}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

final case class RedisConfig(
  endpoint: String,
  integrationCheck: Boolean,
  serverMode: RedisServerMode,
  authMode: RedisAuthMode,
  user: Option[String],
  password: Option[String],
  awsUserId: Option[String],
  awsReplicationGroupId: Option[String],
  awsRegion: Option[String],
) {
  val endpoints: NonEmptyList[String] = NonEmptyList.fromListUnsafe(endpoint.split(",").toList)
  val auth: RedisAuth                 = authMode match {
    case RedisAuthMode.AwsIamAuthMode => RedisAuth.AwsIAM.from(awsUserId, awsReplicationGroupId, awsRegion)
    case RedisAuthMode.PassAuthMode   => RedisAuth.UserPassword.from(user, password)
    case RedisAuthMode.NoAuthMode     => RedisAuth.NoAuth
  }
}

object RedisConfig {
  sealed trait RedisServerMode
  object RedisServerMode {
    implicit val pureconfigReader: ConfigReader[RedisServerMode] = ConfigReader.fromString[RedisServerMode] {
      case "SINGLE"     => Right(SingleMode)
      case "CLUSTER"    => Right(ClusterMode)
      case "REPLICATED" => Right(ReplicatedMode)
      case other        => Left(CannotConvert(other, "ServerMode", s"Unknown Redis Server mode: $other. Available: SINGLE, CLUSTER, REPLICATED."))
    }
    implicit val configMeta: DIConfigMeta[RedisServerMode]     = DIConfigMeta(ConfigMetaType.TBasic(ConfigMetaBasicType.TString))
    implicit val configReader: DIConfigReader[RedisServerMode] = DIConfigReader.deriveFromExistingPureconfigConfigReader[RedisServerMode]

    case object SingleMode extends RedisServerMode
    case object ClusterMode extends RedisServerMode
    case object ReplicatedMode extends RedisServerMode
  }

  sealed trait RedisAuthMode
  object RedisAuthMode {
    implicit val pureconfigReader: ConfigReader[RedisAuthMode] = ConfigReader.fromString[RedisAuthMode] {
      case "PUBLIC"  => Right(NoAuthMode)
      case "PASS"    => Right(PassAuthMode)
      case "AWS_IAM" => Right(AwsIamAuthMode)
      case other     => Left(CannotConvert(other, "AuthMode", s"Unknown Redis Auth mode: $other. Available: AWS_IAM, PASS, PUBLIC."))
    }
    implicit val configMeta: DIConfigMeta[RedisAuthMode]     = DIConfigMeta(ConfigMetaType.TBasic(ConfigMetaBasicType.TString))
    implicit val configReader: DIConfigReader[RedisAuthMode] = DIConfigReader.deriveFromExistingPureconfigConfigReader[RedisAuthMode]

    case object NoAuthMode extends RedisAuthMode
    case object PassAuthMode extends RedisAuthMode
    case object AwsIamAuthMode extends RedisAuthMode
  }

  sealed trait RedisAuth
  object RedisAuth {
    case object NoAuth extends RedisAuth
    final case class UserPassword(user: String, password: String) extends RedisAuth
    object UserPassword {
      def from(user: Option[String], password: Option[String]): UserPassword = {
        {
          for {
            user <- user.filter(_.nonEmpty)
            pass <- password.filter(_.nonEmpty)
          } yield RedisAuth.UserPassword(user, pass)
        }.getOrElse {
          throw new IllegalArgumentException(s"Wrong configuration for PASS mode, required fields: user, password.")
        }
      }
    }
    final case class AwsIAM(awsUserId: String, awsReplicationGroupId: String, awsRegion: String) extends RedisAuth
    object AwsIAM {
      def from(awsUserId: Option[String], awsReplicationGroupId: Option[String], awsRegion: Option[String]): AwsIAM = {
        {
          for {
            user <- awsUserId.filter(_.nonEmpty)
            rep  <- awsReplicationGroupId.filter(_.nonEmpty)
            reg  <- awsRegion.filter(_.nonEmpty)
          } yield RedisAuth.AwsIAM(user, rep, reg)
        }.getOrElse {
          throw new IllegalArgumentException(s"Wrong configuration for AWS_IAM mode, required fields: awsUserId, awsReplicationGroupId, awsRegion.")
        }
      }
    }
  }
}
