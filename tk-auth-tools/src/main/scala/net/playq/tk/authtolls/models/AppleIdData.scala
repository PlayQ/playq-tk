package net.playq.tk.authtolls.models

final case class AppleIdData(userId: String, email: Option[String], emailVerified: Option[Boolean])
