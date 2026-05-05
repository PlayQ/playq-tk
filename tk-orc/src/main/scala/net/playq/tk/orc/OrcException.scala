package net.playq.tk.orc

abstract class OrcException(message: String, cause: Option[Throwable]) extends RuntimeException(message, cause.orNull)

object OrcException {
  final case class SchemaException(message: String) extends OrcException(s"Wrong ORC schema: $message", None)
  final case class ValueException(message: String) extends OrcException(s"Wrong ORC value: $message", None)
}
