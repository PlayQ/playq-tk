package net.playq.tk.kafka.data

import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer, StringDeserializer}

import scala.annotation.unchecked.uncheckedVariance
import scala.language.implicitConversions

final class KafkaDeserializer[+A](val underlying: Deserializer[A @uncheckedVariance]) extends AnyVal

object KafkaDeserializer extends KafkaDeserializerLowPriorityInstances {
  @inline final def apply[A](enc: Deserializer[A])(implicit d: DummyImplicit): KafkaDeserializer[A] = new KafkaDeserializer(enc)
  @inline final def apply[A](implicit k: KafkaDeserializer[A]): k.type                              = k

  implicit final def stringDeserializer: KafkaDeserializer[String] = new KafkaDeserializer(new StringDeserializer)

  implicit final def convertNativeDeserializer[A](enc: Deserializer[A]): KafkaDeserializer[A] = new KafkaDeserializer(enc)
}
sealed trait KafkaDeserializerLowPriorityInstances {
  implicit final def bytesDeserializer: KafkaDeserializer[Array[Byte]] = new KafkaDeserializer(new ByteArrayDeserializer)
}
