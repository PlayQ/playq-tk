package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import net.playq.tk.health.HealthChecker
import net.playq.tk.kafka.clients.KafkaClients
import net.playq.tk.kafka.docker.KafkaDocker
import net.playq.tk.kafka.health.KafkaHealthCheck
import zio.{IO, Task}

object KafkaPlugin extends PluginDef {
  include(KafkaDocker.module[Task]("kafka"))

  make[KafkaHealthCheck[IO]].fromResource[KafkaHealthCheck.Impl[IO]]

  many[HealthChecker[IO]]
    .weak[KafkaHealthCheck[IO]]

  make[KafkaClients[IO]].from[KafkaClients.Impl[IO]]
}
