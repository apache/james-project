/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one    *
 * or more contributor license agreements.  See the NOTICE file  *
 * distributed with this work for additional information         *
 * regarding copyright ownership.  The ASF licenses this file    *
 * to you under the Apache License, Version 2.0 (the             *
 * "License"); you may not use this file except in compliance    *
 * with the License.  You may obtain a copy of the License at    *
 *                                                               *
 * http://www.apache.org/licenses/LICENSE-2.0                    *
 *                                                               *
 * Unless required by applicable law or agreed to in writing,    *
 * software distributed under the License is distributed on an   *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY        *
 * KIND, either express or implied.  See the License for the     *
 * specific language governing permissions and limitations       *
 * under the License.                                            *
 * ************************************************************** */

package org.apache.james.queue.pulsar

import com.sksamuel.pulsar4s.{Consumer, ConsumerConfig, ConsumerMessage, MessageId, Producer, ProducerConfig, PulsarAsyncClient, PulsarClient, Subscription, Topic}
import org.apache.pulsar.client.api.{Schema, SubscriptionInitialPosition, SubscriptionType}

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.Try

case class JavaClient(brokerUri: String, topic: String) {
  private val client: PulsarAsyncClient = PulsarClient(brokerUri)
  private val producerConfig: ProducerConfig = ProducerConfig(Topic(topic), enableBatching = Some(false))
  private val producer: Producer[String] = client.producer(producerConfig)(Schema.STRING)

  def send(payload: String): Try[MessageId] = producer.send(payload)

  private val consumer: Consumer[String] =  client.consumer[String](
    ConsumerConfig(
      subscriptionName = Subscription("subscription"),
      topics = Seq(Topic(topic)),
      subscriptionType = Some(SubscriptionType.Shared),
      subscriptionInitialPosition = Some(SubscriptionInitialPosition.Earliest),
      deadLetterPolicy = Some(DeadLetterPolicy.builder()
                                   .maxRedeliverCount(10)
                                   .deadLetterTopic("persistent://${config.namespace.asString}/James-${name.asString()}/dead-letter")
                                   .build())
    )
  )(Schema.STRING)

  def consumeOne: Option[ConsumerMessage[String]] = consumer.receive(FiniteDuration(1, SECONDS)).toOption.flatten
}