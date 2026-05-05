package net.playq.tk.test.config

import scala.concurrent.duration.FiniteDuration

final case class TestDeadlineConfig(totalBudget: FiniteDuration)
