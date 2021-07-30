package net.playq.tk.aws.sqs.clients

import distage.{Tag, TagKK}
import izumi.distage.constructors.ClassConstructor
import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio.{Clock2, F, IO2}
import logstage.LogIO2
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.aws.sqs.{SQSComponent, SQSQueueId, SQSQueuesManager}
import net.playq.tk.quantified.{ConcurrentThrowable, TimerThrowable}

import java.util.concurrent.ConcurrentHashMap

trait SQSClient[F[+_, +_], +QueueId <: SQSQueueId] extends SQSClientBase[F, QueueId] {
  def getVirtual(name: String): F[Throwable, SQSClientBase[F, SQSQueueId.Virtual[QueueId]]]
}

object SQSClient {

  def sqsModuleDef[F[+_, +_]: TagKK, QID <: SQSQueueId: Tag: ClassConstructor]: ModuleDef = new ModuleDef {
    make[QID]
    make[SQSClient[F, QID]].fromEffect((_: SQSQueuesManager[F]).subscribedClient(_: QID))

    many[SQSQueueId]
      .weak[QID]
  }

  final class Impl[F[+_, +_]: IO2: ConcurrentThrowable: TimerThrowable, +QueueId <: SQSQueueId](
    sqsConfig: SQSConfig,
    sqsComponent: SQSComponent[F],
    clock: Clock2[F],
  )(queue: QueueId,
    queueUrl: String,
  )(implicit
    log: LogIO2[F]
  ) extends SQSClientBase.AbstractSQSClient[F, QueueId](sqsConfig, sqsComponent)(queue, queueUrl)
    with SQSClient[F, QueueId] {

    private[this] val known = new ConcurrentHashMap[String, SQSClientBase[F, SQSQueueId.Virtual[QueueId]]]()

    override def getVirtual(name: String): F[Throwable, SQSClientBase[F, SQSQueueId.Virtual[QueueId]]] = {
      Option(known.get(name)).fold {
        for {
          client <- F.sync(new SQSClientVirtual(sqsConfig, sqsComponent, this, clock)(SQSQueueId.Virtual(name, queue)): SQSClientBase[F, SQSQueueId.Virtual[QueueId]])
          _      <- F.sync(known.put(name, client))
        } yield client
      }(F.pure(_))
    }
  }
}
