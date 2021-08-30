package net.playq.tk.loadtool.scenario

import distage.Tag
import izumi.functional.bio.{Async3, Clock3, Exit, F, Local3, Primitives3, Ref3, Temporal3}
import logstage.LogIO3
import net.playq.tk.loadtool.scenario.Scenario.ScenarioConfig
import net.playq.tk.loadtool.scenario.ScenarioContext.ExecutionReport
import net.playq.tk.loadtool.scenario.ScenarioRunner.ScenarioWithReport
import net.playq.tk.loadtool.scenario.bio.ScenarioIO2.ScenarioIO2SyntaxAux
import net.playq.tk.loadtool.scenario.gen.ScenarioGenProvider
import net.playq.tk.util.retry.{RetryPolicy, TkScheduler}
import zio.Has

final class ScenarioRunner[F[-_, +_, +_]: Async3: Primitives3: Local3](
  logger: LogIO3[F],
  clock: Clock3[F],
  scenarioGenProvider: ScenarioGenProvider,
  scenarioIO2Syntax: ScenarioIO2SyntaxAux[F[Any, +_, +_], F[Has[Ref3[F, ScenarioScope]] with Has[ScenarioContext[F[Any, _, _]]], +_, +_]],
)(implicit
  t1: Tag[Ref3[F, ScenarioScope]],
  t2: Tag[ScenarioContext[F[Any, _, _]]],
  sc: TkScheduler[F[Any, +_, +_]],
) {

  private[this] def execute[E, A](
    effect: F[Has[Ref3[F, ScenarioScope]] with Has[ScenarioContext[F[Any, _, _]]], E, A],
    ctx: ScenarioContext[F[Any, _, _]],
  ): F[Any, E, A] = {
    F.mkRef(ScenarioScope.empty).flatMap(scope => effect.provide(Has.allOf[Ref3[F, ScenarioScope], ScenarioContext[F[Any, _, _]]](scope, ctx)))
  }

  def run[E, A](scenario: Scenario[F[Any, +_, +_], E, A], config: ScenarioConfig): F[Any, E, ScenarioWithReport] = {
    val resource = scenario.mkScenario(scenarioIO2Syntax)
    for {
      context <- ScenarioContext(logger("scenario_id" -> scenario.id), clock, scenarioGenProvider)
      _       <- context.logger.info(s"Scenario ${scenario.id} execution started ...")

      _ <- F.bracket {
        context.logger.info(s"Setup ...") *>
        execute(resource.acquire, context).sandboxExit.flatMap {
          case Exit.Success(v)    => F.pure(Option(v))
          case Exit.Error(err, _) => context.logger.error(s"Setup failed due to: $err.") *> F.pure(Option.empty[resource.InnerResource])
          case Exit.Termination(err, all, _) =>
            context.logger.crit(s"Setup terminated due to: $err, $all.") *> F.pure(Option.empty[resource.InnerResource])
          case Exit.Interruption(err, _) =>
            context.logger.crit(s"Setup interrupted by $err") *> F.pure(Option.empty[resource.InnerResource])
        }
      } {
        _.fold(F.unit) {
          ctx =>
            context.logger.info(s"Cleanup  ...") *>
            execute(resource.release(ctx), context).sandboxExit.flatMap {
              case Exit.Success(_)               => F.unit
              case Exit.Error(err, _)            => context.logger.error(s"Cleanup failed due to: $err.")
              case Exit.Termination(err, all, _) => context.logger.crit(s"Cleanup terminated due to: $err, $all.")
              case Exit.Interruption(err, _)     => context.logger.crit(s"Clean up interrupted by $err")
            }
        }
      } {
        _.fold(F.unit) {
          ctx =>
            context.logger.info(s"Execute ...") *>
            F.parTraverse_(0 until config.onUsers) {
              _ =>
                sc.repeat {
                  for {
                    _ <-
                      execute(resource.extract(ctx).fold(identity, scenarioIO2Syntax.pure).flatMap(identity), context).sandboxExit.flatMap {
                        case Exit.Success(_)               => F.unit
                        case Exit.Error(err, _)            => context.fail *> context.logger.error(s"Execution failed with: $err.")
                        case Exit.Termination(err, all, _) => context.fail *> context.logger.crit(s"Execution terminated with: $err, $all.")
                        case Exit.Interruption(err, _)     => context.fail *> context.logger.crit(s"Execution interrupted with: $err")
                      }
                    _ <- context.executed
                  } yield ()
                }(RetryPolicy.spaced[F[Any, +_, +_]](config.pauseForUser) && RetryPolicy.recurs[F[Any, +_, +_]](config.userScenarioRepeat)).void: F[Any, Nothing, Unit]
            }
        }
      }
      _   <- context.logReport
      res <- context.report
    } yield ScenarioWithReport(scenario.id, res)
  }

}

object ScenarioRunner {
  final case class ScenarioWithReport(id: String, report: ExecutionReport)
}
