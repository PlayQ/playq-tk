package net.playq.tk.control.fiber
import net.playq.tk.control.Forever

trait FiberEffect[F[_, _]] {
  def runInFiber(): F[Nothing, Forever]
  val fiberName: String
}
