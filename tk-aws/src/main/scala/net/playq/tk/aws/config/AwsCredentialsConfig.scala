package net.playq.tk.aws.config

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentialsProvider, StaticCredentialsProvider}

/** Credentials used only for tests with local AWS containers */
final case class AwsCredentialsConfig(
  region: Option[String],
  provider: Option[AwsCredentialsProvider],
) {
  def withSecretKey(accessKeyId: String, secretAccessKey: String): AwsCredentialsConfig = {
    copy(provider = Some(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))))
  }
  def withDummyDockerCredentials: AwsCredentialsConfig = {
    withSecretKey("abc", "abc")
  }
}

object AwsCredentialsConfig {
  def empty: AwsCredentialsConfig                                                   = AwsCredentialsConfig(None, None)
  def secretKey(accessKeyId: String, secretAccessKey: String): AwsCredentialsConfig = {
    AwsCredentialsConfig(None, Some(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))))
  }
  def testing: AwsCredentialsConfig = AwsCredentialsConfig(Some("us-east-1"), None)
}
