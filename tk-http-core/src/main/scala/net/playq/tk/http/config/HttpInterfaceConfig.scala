package net.playq.tk.http.config

import net.playq.tk.http.config.HttpInterfaceConfig.HttpPortRange

final case class HttpInterfaceConfig(
  host: String,
  portRange: HttpPortRange,
  defaultInterfaceAddressOverride: Option[String],
) {
  override def toString: String = s"$host:$portRange"
}

object HttpInterfaceConfig {
  final case class HttpPortRange(min: Int, max: Int) {
    def toShuffledList: List[Int] = {
      scala.util.Random.shuffle((min to max).toList)
    }

    override def toString: String = s"[$min..$max]"
  }
}
