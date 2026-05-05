package d4s.query.models

import software.amazon.awssdk.services.dynamodb.model.ReturnValue

trait WithReturnValue[+D] {
  def withReturnValue(newReturnValue: ReturnValue): D
}
