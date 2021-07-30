package net.playq.tk
import cats.effect.{Async, Bracket, Concurrent, ConcurrentEffect, ContextShift, Effect, Sync, Timer}
import cats.{Applicative, Functor, Monad, MonadError}

object quantified {

  final type FunctorThrowable[F[_, _]] = Functor[F[Throwable, ?]]
  object FunctorThrowable {
    def apply[F[_, _]: FunctorThrowable]: FunctorThrowable[F] = implicitly
  }

  final type ApplicativeThrowable[F[_, _]] = Applicative[F[Throwable, ?]]
  object ApplicativeThrowable {
    def apply[F[_, _]: ApplicativeThrowable]: ApplicativeThrowable[F] = implicitly
  }

  final type MonadThrowable[F[_, _]] = Monad[F[Throwable, ?]]
  object MonadThrowable {
    def apply[F[_, _]: MonadThrowable]: MonadThrowable[F] = implicitly
  }

  final type MonadErrorThrowable[F[_, _]] = MonadError[F[Throwable, ?], Throwable]
  object MonadErrorThrowable {
    def apply[F[_, _]: MonadErrorThrowable]: MonadErrorThrowable[F] = implicitly
  }

  final type BracketThrowable[F[_, _]] = Bracket[F[Throwable, ?], Throwable]
  object BracketThrowable {
    def apply[F[_, _]: BracketThrowable]: BracketThrowable[F] = implicitly
  }

  final type SyncThrowable[F[_, _]] = Sync[F[Throwable, ?]]
  object SyncThrowable {
    def apply[F[_, _]: SyncThrowable]: SyncThrowable[F] = implicitly
  }

  final type AsyncThrowable[F[_, _]] = Async[F[Throwable, ?]]
  object AsyncThrowable {
    def apply[F[_, _]: AsyncThrowable]: AsyncThrowable[F] = implicitly
  }

  final type EffectThrowable[F[_, _]] = Effect[F[Throwable, ?]]
  object EffectThrowable {
    def apply[F[_, _]: EffectThrowable]: EffectThrowable[F] = implicitly
  }

  final type ConcurrentThrowable[F[_, _]] = Concurrent[F[Throwable, ?]]
  object ConcurrentThrowable {
    def apply[F[_, _]: ConcurrentThrowable]: ConcurrentThrowable[F] = implicitly
  }

  final type ConcurrentEffect2[F[_, _]] = ConcurrentEffect[F[Throwable, ?]]
  object ConcurrentEffect2 {
    def apply[F[_, _]: ConcurrentEffect2]: ConcurrentEffect2[F] = implicitly
  }

  final type TimerThrowable[F[_, _]] = Timer[F[Throwable, ?]]
  object TimerThrowable {
    def apply[F[_, _]: TimerThrowable]: TimerThrowable[F] = implicitly
  }

  final type ContextShiftThrowable[F[_, _]] = ContextShift[F[Throwable, ?]]
  object ContextShiftThrowable {
    def apply[F[_, _]: ContextShiftThrowable]: ContextShiftThrowable[F] = implicitly
  }

}
