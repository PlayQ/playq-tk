package net.playq.tk.util

object newtype {
  type Newsubtype[A <: Upper, Upper] = NewtypeCustom[A, Nothing, Upper]
  type Newtype[A]                    = Newsubtype[A, Any]

  private[newtype] abstract class NewtypeCustom[A >: Lower <: Upper, Lower <: Upper, Upper] {
    private[NewtypeCustom] final object FinalT {
      type T >: Lower <: Upper
    }
    final type T = FinalT.T
    final def apply(value: A): T                                                                         = value.asInstanceOf[T]
    final def apply[Collection[+_]](collection: Collection[A])(implicit q: DummyImplicit): Collection[T] = collection.asInstanceOf[Collection[T]]

    @inline protected[this] def unwrap(value: T): A = value.asInstanceOf[A]
  }
}
