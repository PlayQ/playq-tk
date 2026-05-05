package net.playq.tk.plugins

import izumi.distage.model.definition.Id
import izumi.distage.model.definition.StandardAxis.{Mode, Scene}
import izumi.distage.plugins.PluginDef
import izumi.functional.bio.IO2
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.aws.http.TkAwsHttpClient
import net.playq.tk.concurrent.threadpools.NamingThreadFactory

import java.util.concurrent.ThreadFactory

object AwsCommonPlugin extends PluginDef {
  include(AwsTagsModule)
  make[AwsCredentialsConfig].from(AwsCredentialsConfig.empty).tagged(Mode.Prod)
  make[AwsCredentialsConfig].from(AwsCredentialsConfig.testing).tagged(Mode.Test)
  modify[AwsCredentialsConfig](_.withDummyDockerCredentials).tagged(Scene.Managed)

  make[ThreadFactory].named("aws").from {
    new NamingThreadFactory("tk-aws")
  }

  make[TkAwsHttpClient].fromResource {
    (io: IO2[zio.IO], tf: ThreadFactory @Id("aws")) =>
      new TkAwsHttpClient.Netty[zio.IO](tf)(using io)
  }
}
