package net.playq.tk.postgres

import distage.Axis

object SeparatePostgresConnections extends Axis {
  override def name: String = "separatePostgresConnections"

  case object On extends AxisChoiceDef
  case object Off extends AxisChoiceDef
}
