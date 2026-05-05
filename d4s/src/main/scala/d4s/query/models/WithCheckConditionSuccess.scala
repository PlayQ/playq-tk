package d4s.query.models

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.QueryDecoder
import izumi.functional.bio.{F, IO2}
import software.amazon.awssdk.services.dynamodb.model.{ConditionalCheckFailedException, DynamoDbException, TransactionCanceledException}

import scala.jdk.CollectionConverters.*

trait WithCheckConditionSuccess[DQ[+d] <: DynamoQuery[DQ, d], +Dec] extends DynamoQuery[DQ, Dec] {
  self: DQ[Dec] =>

  final def eitherConditionSuccess: DQ[Either[DynamoDbException /* ConditionalCheckFailedException | TransactionCanceledException */, Dec]] = decodeF {
    new QueryDecoder[Rsp, Either[DynamoDbException, Dec]] {
      override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, Rsp]): F[DynamoException, Either[DynamoDbException, Dec]] = rsp match {
        case Left(DynamoException(_, err: ConditionalCheckFailedException)) =>
          F.pure(Left(err))
        case Left(DynamoException(_, err: TransactionCanceledException)) if err.cancellationReasons().asScala.exists(_.code() == "ConditionalCheckFailed") =>
          F.pure(Left(err))
        case other =>
          decoder.decode[F](other).map(Right(_))
      }
    }
  }

  final def optConditionFailure: DQ[Option[DynamoDbException]] = {
    eitherConditionSuccess.map(_.left.toOption)
  }

  final def boolConditionSuccess: DQ[Boolean] = {
    eitherConditionSuccess.map(_.isRight)
  }
}
