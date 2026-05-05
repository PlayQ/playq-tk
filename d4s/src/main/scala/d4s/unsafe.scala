package d4s

import scala.annotation.implicitNotFound

@implicitNotFound("import d4s.unsafe.unsafeDynamoOps to enable unsafe Dynamo operations.")
sealed abstract class UnsafeDynamoOps

object unsafe {
  implicit def unsafeDynamoOps: UnsafeDynamoOps = null
}
