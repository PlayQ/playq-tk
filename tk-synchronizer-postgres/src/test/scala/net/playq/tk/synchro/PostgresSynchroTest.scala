package net.playq.tk.synchro

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Async2, Entropy2, F, Primitives2, Temporal2}
import net.playq.tk.postgres.test.PostgresTestEnv
import net.playq.tk.synchro.PostgresSynchroTest.Ctx
import net.playq.tk.test.TkTestBaseCtx
import zio.IO

import scala.concurrent.duration.DurationInt

final class PostgresSynchroTest extends PostgresSynchroTestBase[IO]

object PostgresSynchroTest {
  final case class Ctx[F[+_, +_]](
    synchro: TkSynchronizerPostgres[F],
    entropy: Entropy2[F],
  )(implicit
    val p: Primitives2[F],
    val t: Temporal2[F],
  )
}

abstract class PostgresSynchroTestBase[F[+_, +_]: Async2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with PostgresTestEnv[F] {
//  override protected def logLevel: Log.Level = Log.Level.Debug

  "PostgresSynchronizer" should {
    "synchronize actions" in bound {
      ctx =>
        import ctx.*
        for {
          ref <- F.mkRef(false)
          _   <- F.parTraverse(1 to 10) {
            _ =>
              for {
                case Some(_) <- synchro.trySynchronized("test1", 1.minute) {
                  for {
                    get <- ref.get
                    _   <- ref.set(true)
                    _   <- assertIO(!get)
                    _   <- ref.set(false)
                  } yield ()
                }
              } yield ()
          }
        } yield ()
    }

    "release on ttl" in bound {
      ctx =>
        import ctx.*
        for {
          mutex    <- synchro.tryToAcquireMutexUntilSuccess("test2", 200.seconds, 1.seconds)
          attempt1 <- synchro.tryToAcquireMutex("test2", 200.seconds)
          _        <- assertIO(attempt1.isEmpty)
          attempt2 <- synchro.tryToAcquireMutex("test2", 0.seconds)
          _        <- assertIO(attempt2.nonEmpty)
          _        <- synchro.releaseMutex(mutex)
        } yield ()
    }

    "prevent nesting" in bound {
      ctx =>
        import ctx.*
        for {
          lock <- entropy.nextUUID().map(unique => s"test3-$unique")
          res  <- synchro.trySynchronized(lock, 5.minutes) {
            synchro.trySynchronized(lock, 10.seconds)(
              synchro.trySynchronized(lock, 10.seconds)(
                synchro.trySynchronized(lock, 10.seconds)(
                  synchro.trySynchronized(lock, 10.seconds)(
                    synchro.trySynchronized(lock, 10.seconds)(
                      synchro.trySynchronized(lock, 10.seconds)(
                        F.unit
                      )
                    )
                  )
                )
              )
            )
          }
          // noinspection OptionEqualsSome
          _ <- assertIO(res == Some(None))
        } yield ()
    }

    "release is idempotent" in bound {
      ctx =>
        import ctx.*
        for {
          lock <- entropy.nextUUID().map(unique => s"test4-$unique")
          case Some(mutex1) <- synchro.tryToAcquireMutex(lock, 200.seconds)
          _ <- synchro.releaseMutex(mutex1)
          case Some(_) <- synchro.tryToAcquireMutex(lock, 200.seconds)
          _   <- synchro.releaseMutex(mutex1)
          res <- synchro.tryToAcquireMutex(lock, 200.seconds)
          _   <- assertIO(res.isEmpty)
        } yield ()
    }

  }
}
