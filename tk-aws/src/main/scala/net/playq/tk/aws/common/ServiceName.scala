package net.playq.tk.aws.common

final case class ServiceName(serviceName: String) extends AnyVal {
  override def toString: String = serviceName
}
