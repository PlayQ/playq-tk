package net.playq.tk.loadtool.scenario.bio

import izumi.functional.bio.{Clock2, Exit, F, Ref3}
import logstage.LogIO2
import net.playq.tk.loadtool.scenario.bio.ScenarioIO2.ScenarioIO2SyntaxAux
import net.playq.tk.loadtool.scenario.bio.ScenarioIO2Test.Ctx
import net.playq.tk.loadtool.scenario.gen.{ScenarioGen, ScenarioGenProvider}
import net.playq.tk.loadtool.scenario.{ScenarioScope, _}
import net.playq.tk.test.{TkTestBase, WithProduction}
import zio.{Has, IO, ZIO}

class ScenarioIO2Test extends TkTestBase[Ctx] with WithProduction {

  "ScenarioIO2" should {
    "run scenarios" in scopeIO {
      ctx =>
        import ctx.*
        val scenario = for {
          // pure
          _    <- steps.bind(ScenarioGen[Boolean](true))
          int  <- steps.pure(2)
          int2 <- steps.sync("double value sync")(() => int * 2)
          int3 <- steps.fromF("double value fromF")(F.sync(int * 2))
          _     = assert(int2 == 4)
          _     = assert(int3 == 4)
          _ <- steps.step("get bool") {
            b: Boolean => assert(b)
          }

          _ <- steps.bind(ScenarioGen[String]("Test String"))
          _ <- steps.step("get string") {
            s: String => assert(s == "Test String")
          }

          // binds
          _ <- steps.newScope()
          _ <- steps.bind(ScenarioGen[String]("New Test String"))
          _ <- steps.bind(ScenarioGen[Int](int))
          _ <- steps.unrecordedStepF("get int and string") {
            (s: String, i: Int) =>
              F.sync {
                assert(s == "New Test String")
                assert(i == 2)
              }
          }
          _ <- steps.newScope()
          _ <-
            steps
              .step("fail on clean scope") {
                s: String => println(s)
              }.sandboxExit.map {
                result => assert(result.isInstanceOf[Exit.Termination])
              }

          //bracket
          _ <- steps.bracket(steps.pure("acquire"))(
            s =>
              steps.step("release")(
                (binded: String) => {
                  assert(binded == s)
                  ()
                }
              ) *> steps.bind(ScenarioGen[Long](1))
          )(use = s => steps.bind(ScenarioGen[String](s)))

          _ <- steps.step("get string and long")(
            (s: String, l: Long) => {
              assert(s == "acquire")
              assert(l == 1)
            }
          )

          //bracket fail
          _ <- steps.newScope()
          _ <-
            steps
              .pure(1).bracket(i => steps.bind(ScenarioGen[Int](i)))(_ => steps.fromF("error")(F.fail("error").unit))
              .redeem(_ => steps.unit, _ => steps.unit)
          _ <- steps.unrecordedStep("get int after error in use")((i: Int) => { assert(i == 1) })

          // traverse
          list  <- steps.traverse(List(1, 2, 3))(v => steps.pure(v * 2))
          _      = assert(list == List(2, 4, 6))
          empty <- steps.traverse(Nil: List[Int])(v => steps.pure(v * 2))
          _      = assert(empty.isEmpty)
        } yield ()

        (for {
          scope   <- F.mkRef(ScenarioScope.empty)
          context <- ScenarioContext[IO](log, clock, ScenarioGenProvider.empty)
        } yield scenario.provide(Has.allOf[Ref3[ZIO, ScenarioScope], ScenarioContext[ZIO[Any, _, _]]](scope, context))).flatten
    }
  }
}

object ScenarioIO2Test {
  final case class Ctx(
    log: LogIO2[IO],
    clock: Clock2[IO],
    zioClock: zio.clock.Clock,
    steps: ScenarioIO2SyntaxAux[IO, ZIO[Has[Ref3[ZIO, ScenarioScope]] with Has[ScenarioContext[ZIO[Any, _, _]]], +_, +_]],
  )
}
