package net.playq.tk.loadtool.scenario

import izumi.functional.bio.retry.{RetryPolicy, Scheduler2}
import izumi.functional.bio.{Async2, BlockingIO2, Clock2, Exit, F, Fork2, Primitives2, Temporal2}
import logstage.LogIO2
import net.playq.tk.loadtool.scenario.Scenario.ScenarioConfig
import net.playq.tk.loadtool.scenario.ScenarioContext.ExecutionReport
import net.playq.tk.loadtool.scenario.ScenarioF.ScenarioExecutionContext
import net.playq.tk.loadtool.scenario.ScenarioRunner.ScenarioWithReport

import scala.concurrent.duration.DurationInt

final class ScenarioRunner[F[+_, +_]: Async2: Primitives2: BlockingIO2: Fork2: Temporal2: Scheduler2](
  logger: LogIO2[F],
  clock: Clock2[F],
  defaultGens: Set[ScenarioGen.Gen[?]],
) {

  private[this] def execute[E, A](
    effect: ScenarioF[F, E, A],
    ctx: ScenarioContext[F],
  ): F[E, A] = {
    val gens         = ScenarioGens(defaultGens)
    val execCtx      = ScenarioExecutionContext(ctx, gens)
    val rechargeLoop = F.shiftBlocking(gens.rechargeGenerators(5).flatMap(_ => F.sleep(100.millis)).forever)
    F.bracket(rechargeLoop.fork)(_.interrupt)(_ => effect.eff(execCtx))
  }

  def run[E, A](scenario: Scenario[F, E, A], config: ScenarioConfig): F[Nothing, ScenarioWithReport] = {
    val builder = scenario.mkScenario
    for {
      context <- ScenarioContext(logger("scenario_id" -> scenario.id), clock)
      _       <- context.logger.info(s"Scenario ${scenario.id} started ...")

      _ <- F.bracket(
        acquire = buildScenario(builder, context)
      )(
        release = F.traverse_(_)(releaseScenario(builder, context))
      )(
        use = F.traverse_(_)(executeScenario(builder, context, config))
      )
      _   <- context.logReport
      res <- context.report
    } yield ScenarioWithReport(scenario.id, res)
  }

  private def executeScenario[E, A](
    builder: ScenarioBuilder[F, E, A],
    context: ScenarioContext[F],
    config: ScenarioConfig,
  )(resource: builder.InnerResource
  ): F[Nothing, Unit] = {
    val repeatPolicy = RetryPolicy.spaced[F](config.pauseForUser) && RetryPolicy.recurs[F](config.userScenarioRepeat)
    context.logger.info(s"Execute ...") *>
    F.parTraverse_(0 until config.onUsers) {
      userIdx =>
        setupScenario(builder, context, userIdx)(resource).flatMap {
          F.traverse_(_) {
            scenario =>
              F.repeat {
                for {
                  _ <- execute(scenario, context).sandboxExit.flatMap {
                    case Exit.Success(_)                   => F.unit
                    case Exit.Error(err, trace)            => context.fail *> context.logger.error(s"Execution failed with: $err\n$trace")
                    case Exit.Termination(err, all, trace) => context.fail *> context.logger.crit(s"Execution terminated with: $err\n$all\n$trace")
                  }
                  _ <- context.executed
                } yield ()
              }(repeatPolicy).void
          }
        }
    }
  }

  private def setupScenario[E, A](
    builder: ScenarioBuilder[F, E, A],
    context: ScenarioContext[F],
    userIdx: Int,
  )(resource: builder.InnerResource
  ): F[Nothing, Option[SF[F, E, A]]] = {
    context.logger.info(s"User $userIdx scenario setup ...") *>
    handleErrors(context, "User scenario setup")(execute(builder.extract(resource).fold(identity, SF.pure(_)), context))
  }

  private def buildScenario[E, A](builder: ScenarioBuilder[F, E, A], context: ScenarioContext[F]): F[Nothing, Option[builder.InnerResource]] = {
    context.logger.info(s"Building ...") *>
    handleErrors(context, "Building")(execute(builder.acquire, context))
  }

  private def releaseScenario[E, A](builder: ScenarioBuilder[F, E, A], context: ScenarioContext[F])(resource: builder.InnerResource): F[Nothing, Unit] = {
    context.logger.info(s"Cleanup  ...") *>
    handleErrors(context, "Cleanup")(execute(builder.release(resource), context)).void
  }

  private def handleErrors[E, A](context: ScenarioContext[F], phase: String)(eff: F[E, A]): F[Nothing, Option[A]] = {
    eff.sandboxExit.flatMap {
      case Exit.Success(v)               => F.pure(Some(v))
      case Exit.Error(err, _)            => context.logger.error(s"Scenario $phase failed due to: $err.").as(None)
      case Exit.Termination(err, all, _) => context.logger.crit(s"Scenario $phase terminated due to: $err, $all.").as(None)
    }
  }
}

object ScenarioRunner {
  final case class ScenarioWithReport(id: String, report: ExecutionReport) {
    override def toString: String = {
      s"""Scenario $id report:
         |${report.toString.split("\n").map("\t" + _).mkString("\n")}
         |""".stripMargin
    }
  }
}
