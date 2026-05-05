package d4s.query.models

import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure

trait WithReturnValuesOnConditionCheckFailure[+D] {
  def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): D
}
