package net.playq.tk.kafka.data

import org.apache.kafka.common.serialization.{ByteArraySerializer, Serializer, StringSerializer}

import scala.annotation.unchecked.uncheckedVariance
import scala.language.implicitConversions

final class KafkaSerializer[-A](val underlying: Serializer[A @uncheckedVariance]) extends AnyVal

object KafkaSerializer extends KafkaSerializerLowPriorityInstances {
  @inline final def apply[A](enc: Serializer[A])(implicit d: DummyImplicit): KafkaSerializer[A] = new KafkaSerializer(enc)
  @inline final def apply[A](implicit k: KafkaSerializer[A]): k.type                            = k

  implicit final def stringSerializer: KafkaSerializer[String] = new KafkaSerializer(new StringSerializer)

  implicit final def convertNativeSerializer[A](enc: Serializer[A]): KafkaSerializer[A] = new KafkaSerializer(enc)
}
sealed trait KafkaSerializerLowPriorityInstances {
  implicit final def bytesSerializer: KafkaSerializer[Array[Byte]] = new KafkaSerializer(new ByteArraySerializer)
}
