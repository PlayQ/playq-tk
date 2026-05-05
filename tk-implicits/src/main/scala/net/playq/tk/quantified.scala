package net.playq.tk

import cats.effect.{Async, Concurrent, MonadCancel, Spawn, Sync, Temporal}
import cats.{Applicative, Functor, Monad, MonadError}

object quantified {

  final type FunctorThrowable[F[_, _]] = Functor[F[Throwable, _]]
  object FunctorThrowable {
    def apply[F[_, _]: FunctorThrowable]: FunctorThrowable[F] = implicitly
  }

  final type ApplicativeThrowable[F[_, _]] = Applicative[F[Throwable, _]]
  object ApplicativeThrowable {
    def apply[F[_, _]: ApplicativeThrowable]: ApplicativeThrowable[F] = implicitly
  }

  final type MonadThrowable[F[_, _]] = Monad[F[Throwable, _]]
  object MonadThrowable {
    def apply[F[_, _]: MonadThrowable]: MonadThrowable[F] = implicitly
  }

  final type MonadErrorThrowable[F[_, _]] = MonadError[F[Throwable, _], Throwable]
  object MonadErrorThrowable {
    def apply[F[_, _]: MonadErrorThrowable]: MonadErrorThrowable[F] = implicitly
  }

  final type MonadCancelThrowable[F[_, _]] = MonadCancel[F[Throwable, _], Throwable]
  object MonadCancelThrowable {
    def apply[F[_, _]: MonadCancelThrowable]: MonadCancelThrowable[F] = implicitly
  }

  final type SyncThrowable[F[_, _]] = Sync[F[Throwable, _]]
  object SyncThrowable {
    def apply[F[_, _]: SyncThrowable]: SyncThrowable[F] = implicitly
  }

  final type SpawnThrowable[F[_, _]] = Spawn[F[Throwable, _]]
  object SpawnThrowable {
    def apply[F[_, _]: SpawnThrowable]: SpawnThrowable[F] = implicitly
  }

  final type ConcurrentThrowable[F[_, _]] = Concurrent[F[Throwable, _]]
  object ConcurrentThrowable {
    def apply[F[_, _]: ConcurrentThrowable]: ConcurrentThrowable[F] = implicitly
  }

  final type TemporalThrowable[F[_, _]] = Temporal[F[Throwable, _]]
  object TemporalThrowable {
    def apply[F[_, _]: TemporalThrowable]: TemporalThrowable[F] = implicitly
  }

  final type AsyncThrowable[F[_, _]] = Async[F[Throwable, _]]
  object AsyncThrowable {
    def apply[F[_, _]: AsyncThrowable]: AsyncThrowable[F] = implicitly
  }

}
