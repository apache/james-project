package org.apache.james.backends.pulsar

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.dimafeng.testcontainers.{ForAllTestContainer, PulsarContainer}
import com.sksamuel.pulsar4s.{ProducerConfig, PulsarClient, Topic}
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContextExecutor

class PulsarReaderTest extends org.scalatest.wordspec.AsyncWordSpec with ForAllTestContainer with Matchers {
  implicit val actorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
  override val container: PulsarContainer = PulsarContainer("2.9.1")
  implicit lazy val client = PulsarClient(container.pulsarBrokerUrl())

  "it" should {
    "return 0 records when topic is empty" in {
      val actual = PulsarReader.forTopic(Topic("foo")).runWith(Sink.seq)
      actual.map(seq => assert(seq.isEmpty))
      actual.map(seq => seq should be(Symbol("empty")))
    }
    "return all records previously produced in the topic" in {
      import PulsarReader.schemas.schema
      val topic = Topic("bar")
      val producer = client.producer(ProducerConfig(topic))
      val messages = 1.to(10).map(i => "message $i")
      messages.foreach(message => producer.send(message))
      val actual = PulsarReader.forTopic(topic).runWith(Sink.seq)
      actual.map(seq => seq.map(_.value) should equal(messages))
    }
  }
}
