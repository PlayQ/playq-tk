package d4s.query.models

import software.amazon.awssdk.services.dynamodb.model.Select

trait WithSelect[+D] {
  def withSelect(newSelect: Select): D
}
