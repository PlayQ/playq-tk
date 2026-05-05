package net.playq.tk.util.data.convert

trait DataDecoder[From, To] {
  self =>
  def decode(from: From): To
  final def map[A](f: To => A): DataDecoder[From, A] = new DataDecoder[From, A] {
    override def decode(from: From): A = f(self.decode(from))
  }
}
