package net.playq.tk.util.data.convert

trait DataEncoder[From, To] {
  def encode(from: From): To
}
