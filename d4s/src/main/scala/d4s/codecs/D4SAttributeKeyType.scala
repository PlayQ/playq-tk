package d4s.codecs

import java.util.UUID

import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType

trait D4SAttributeKeyType[T] {
  def attrType: ScalarAttributeType
}

object D4SAttributeKeyType {
  def apply[T: D4SAttributeKeyType]: D4SAttributeKeyType[T] = implicitly

  def apply[T](scalarAttributeType: ScalarAttributeType): D4SAttributeKeyType[T] = new D4SAttributeKeyType[T] {
    override def attrType: ScalarAttributeType = scalarAttributeType
  }

  def S[T]: D4SAttributeKeyType[T] = D4SAttributeKeyType(ScalarAttributeType.S)
  def N[T]: D4SAttributeKeyType[T] = D4SAttributeKeyType(ScalarAttributeType.N)
  def B[T]: D4SAttributeKeyType[T] = D4SAttributeKeyType(ScalarAttributeType.B)

  implicit val stringAttribute: D4SAttributeKeyType[String] = D4SAttributeKeyType.S
  implicit val uuidAttribute: D4SAttributeKeyType[UUID]     = D4SAttributeKeyType.S

  implicit val byteAttribute: D4SAttributeKeyType[Byte]   = D4SAttributeKeyType.N
  implicit val shortAttribute: D4SAttributeKeyType[Short] = D4SAttributeKeyType.N
  implicit val intAttribute: D4SAttributeKeyType[Int]     = D4SAttributeKeyType.N
  implicit val longAttribute: D4SAttributeKeyType[Long]   = D4SAttributeKeyType.N

  implicit val sdkBytesAttribute: D4SAttributeKeyType[SdkBytes]     = D4SAttributeKeyType.B
  implicit val arrayByteAttribute: D4SAttributeKeyType[Array[Byte]] = D4SAttributeKeyType.B
}
