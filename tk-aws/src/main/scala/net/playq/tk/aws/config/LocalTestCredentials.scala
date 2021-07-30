package net.playq.tk.aws.config

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentialsProvider, StaticCredentialsProvider}

/** Credentials used only for tests with local AWS containers */
final case class LocalTestCredentials(get: AwsCredentialsProvider) extends AnyVal

object LocalTestCredentials {
  def default: LocalTestCredentials = LocalTestCredentials(StaticCredentialsProvider.create(AwsBasicCredentials.create("abc", "cba")))
}
