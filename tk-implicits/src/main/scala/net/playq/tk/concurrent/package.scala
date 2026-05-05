package net.playq.tk

package object concurrent {
  val AvailableProcessors: Int = sys.runtime.availableProcessors().max(2)
}
