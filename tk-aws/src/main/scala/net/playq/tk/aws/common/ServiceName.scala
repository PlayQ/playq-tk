package net.playq.tk.aws.common

sealed trait ServiceName {
  def toOption: Option[String]
}

object ServiceName {
  def apply(serviceName: String): ServiceName = Named(serviceName)

  case object Empty extends ServiceName {
    override def toString: String         = "unassigned"
    override def toOption: Option[String] = None
  }

  final case class Named(serviceName: String) extends ServiceName {
    override def toString: String         = serviceName
    override def toOption: Option[String] = Some(serviceName)
  }
}
