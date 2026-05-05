package net.playq.tk.loadtool.scenario

import distage.{Lifecycle, TagKK}
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Clock2, Exit, F, IO2, Primitives2}
import izumi.logstage.api.IzLogger
import logstage.LogIO2
import net.playq.tk.loadtool.scenario.SF.ScenarioExecutionContext
import net.playq.tk.loadtool.scenario.Scenario.ScenarioConfig
import net.playq.tk.loadtool.scenario.ScenarioTest.{Ctx, TestBind}
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import zio.IO

import scala.concurrent.duration.DurationInt

final class ScenarioTestProduction extends ScenarioTest[IO] with WithProduction

abstract class ScenarioTest[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  "Scenario" should {
    "support base F syntax" in bound {
      ctx =>
        import ctx.*
        val scenario = for {
          // pure
          _    <- SF.genBindConst(true)
          int  <- SF.pure(2)
          int2 <- SF.sync("double value sync")(int * 2)
          int3 <- SF.lift("double value fromF")(F.sync(int * 2))
          _     = assert(int2 == 4)
          _     = assert(int3 == 4)
          _    <- SF.syncWith("get bool") {
            (b: Boolean) => assert(b)
          }

          _ <- SF.genBindConst("Test String")
          _ <- SF.syncWith("get string") {
            (s: String) => assert(s == "Test String")
          }

          // binds
          _ <- SF.genReset()
          _ <- SF.genBindConst("New Test String")
          _ <- SF.genBindMany(ScenarioGen[Int](int))
          _ <- SF.liftUnrecordedWith {
            (s: String, i: Int) =>
              F.sync {
                assert(s == "New Test String")
                assert(i == 2)
              }
          }
          _ <- SF.genReset()
          _ <-
            SF
              .syncWith("fail on clean scope") {
                (s: String) => logger.info(s"print $s")
              }.sandboxExit.map {
                result => assert(result.isInstanceOf[Exit.Termination])
              }

          // bracket
          _ <- SF.bracket(SF.pure("acquire"))(
            s =>
              SF.syncWith("release")(
                (binded: String) => {
                  assert(binded == s)
                  ()
                }
              ) *> SF.genBindConst(1L)
          )(use = s => SF.genBindConst(s))

          _ <- SF.syncWith("get string and long")(
            (s: String, l: Long) => {
              assert(s == "acquire")
              assert(l == 1)
            }
          )

          // bracket fail
          _ <- SF.genReset()
          _ <-
            SF
              .pure(1).bracket(i => SF.genBindConst(i))(_ => SF.lift("error")(F.fail("error").void))
              .redeem(_ => SF.unit, _ => SF.unit)
          _ <- SF.syncUnrecordedWith((i: Int) => { assert(i == 1) })

          // traverse
          list  <- SF.traverse(List(1, 2, 3))(v => SF.pure(v * 2))
          _      = assert(list == List(2, 4, 6))
          empty <- SF.traverse(Nil: List[Int])(v => SF.pure(v * 2))
          _      = assert(empty.isEmpty)
        } yield ()

        for {
          context <- ScenarioContext(log, clock)
          gens     = ScenarioGens.empty
          _       <- scenario.eff(ScenarioExecutionContext(context, gens))
        } yield ()
    }

    "run with runner" in bound {
      ctx =>
        import ctx.*
        val scenario = new Scenario[F, Nothing, Unit] {
          override val id: String                                                                   = "test"
          override def mkScenario: Lifecycle[ScenarioF[F, Nothing, _], ScenarioF[F, Nothing, Unit]] = Lifecycle.liftF {
            for {
              list  <- SF.traverse(List(1, 2, 3))(v => SF.pure(v * 2))
              _      = assert(list == List(2, 4, 6))
              empty <- SF.traverse(Nil: List[Int])(v => SF.pure(v * 2))
              _      = assert(empty.isEmpty)
            } yield {
              SF.lift("assert")(assertIO(list == List(2, 4, 6))).void
            }
          }
        }

        runner.run(scenario, ScenarioConfig("test", 1, 10.millis, 1))
    }

    "support functoid generators" in bound {
      ctx =>
        import ctx.*
        val scenario = new Scenario[F, Nothing, Unit] {
          override val id: String = "test"

          override def mkScenario: Lifecycle[ScenarioF[F, Nothing, _], ScenarioF[F, Nothing, Unit]] = Lifecycle.liftF {
            for {
              _   <- SF.genBindFrom((str: String, long: Long) => TestBind(s"$str:$long"))
              res <- SF.liftUnrecordedWith((t: TestBind) => assertIO(t.str.nonEmpty).as(t.str))
            } yield {
              SF.lift("assert")(assertIO(res.nonEmpty)).void
            }
          }
        }

        runner.run(scenario, ScenarioConfig("test", 1, 10.millis, 1))
    }
  }
}

object ScenarioTest {
  final case class TestBind(str: String)
  final case class Ctx[F[+_, +_]](
    log: LogIO2[F],
    clock: Clock2[F],
    runner: ScenarioRunner[F],
    logger: IzLogger,
  )(implicit
    val p: Primitives2[F]
  )
}
