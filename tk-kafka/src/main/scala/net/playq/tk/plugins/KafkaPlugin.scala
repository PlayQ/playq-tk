package net.playq.tk.plugins

import distage.plugins.PluginDef
import net.playq.tk.kafka.KafkaAdminClient
import net.playq.tk.kafka.docker.KafkaDocker
import zio.{IO, Task}

object KafkaPlugin extends PluginDef {
  include(KafkaDocker.module[Task]("kafka"))
  make[KafkaAdminClient[IO]].from[KafkaAdminClient.Impl[IO]]
}
