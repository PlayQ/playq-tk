package net.playq.tk.health

sealed trait TgHealthState
object TgHealthState {
  final case object OK extends TgHealthState { override def toString: String = "OK" }
  final case object DEFUNCT extends TgHealthState { override def toString: String = "DEFUNCT" }
  final case object UNKNOWN extends TgHealthState { override def toString: String = "UNKNOWN" }
}
