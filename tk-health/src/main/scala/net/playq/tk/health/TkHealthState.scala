package net.playq.tk.health

sealed trait TkHealthState
object TkHealthState {
  case object OK extends TkHealthState { override def toString: String = "OK" }
  case object DEFUNCT extends TkHealthState { override def toString: String = "DEFUNCT" }
  case object UNKNOWN extends TkHealthState { override def toString: String = "UNKNOWN" }
}
