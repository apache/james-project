/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.pulsar

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
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
