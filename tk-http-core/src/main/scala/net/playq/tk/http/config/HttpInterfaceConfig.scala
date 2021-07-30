package net.playq.tk.http.config

import HttpInterfaceConfig.HttpPortRange

final case class HttpInterfaceConfig(host: String, portRange: HttpPortRange, apiVersion: Option[String])

object HttpInterfaceConfig {
  final case class HttpPortRange(min: Int, max: Int) {
    def toShuffledList: List[Int] = {
      scala.util.Random.shuffle((min to max).toList)
    }
  }
}
