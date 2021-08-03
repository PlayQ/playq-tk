package net.playq.tk.health

sealed trait TkHealthState
object TkHealthState {
  final case object OK extends TkHealthState { override def toString: String = "OK" }
  final case object DEFUNCT extends TkHealthState { override def toString: String = "DEFUNCT" }
  final case object UNKNOWN extends TkHealthState { override def toString: String = "UNKNOWN" }
}
