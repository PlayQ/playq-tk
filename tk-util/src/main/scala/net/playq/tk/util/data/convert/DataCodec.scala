package net.playq.tk.util.data.convert

trait DataCodec[From, To] extends DataEncoder[From, To] with DataDecoder[To, From] {
  self =>
  final def imap[A](f: From => A)(g: A => From): DataCodec[A, To] = new DataCodec[A, To] {
    override def decode(from: To): A = f(self.decode(from))
    override def encode(from: A): To = self.encode(g(from))
  }
  final def decoder: DataDecoder[To, From] = new DataDecoder[To, From] {
    override def decode(from: To): From = self.decode(from)
  }
  final def encoder: DataEncoder[From, To] = new DataEncoder[From, To] {
    override def encode(from: From): To = self.encode(from)
  }
}
