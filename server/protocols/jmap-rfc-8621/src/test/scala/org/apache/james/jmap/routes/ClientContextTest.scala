/***************************************************************
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

package org.apache.james.jmap.routes

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.Registration
import org.apache.james.jmap.change.{EmailTypeName, StateChangeEvent, StateChangeListener}
import org.apache.james.jmap.core.{OutboundMessage, UuidState}
import org.apache.james.mailbox.MailboxSessionUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Sinks

class ClientContextTest {
  private val bob = Username.of("bob")
  private val event = StateChangeEvent(eventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4"),
    username = bob,
    map = Map(EmailTypeName -> UuidState.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943")))

  @Test
  def cleanShouldCompleteTheSinkWhenAPushIsBeingEmitted(): Unit = {
    val sink: Sinks.Many[OutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val context = ClientContext(sink, new AtomicReference[Registration](), MailboxSessionUtil.create(bob))
    val listener = StateChangeListener(Set(EmailTypeName), sink)

    val emitting = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    val completed = new AtomicBoolean(false)
    // The WebSocket encodes and writes the push inside the emission
    sink.asFlux().subscribe(_ => {
        emitting.countDown()
        release.await(10, TimeUnit.SECONDS)
      },
      _ => (),
      () => completed.set(true))

    val pusher = new Thread(() => listener.event(event))
    pusher.start()
    assertThat(emitting.await(10, TimeUnit.SECONDS)).isTrue

    val failure = new AtomicReference[Throwable]()
    val closer = new Thread(() => try {
      context.clean()
    } catch {
      case e: Throwable => failure.set(e)
    })
    closer.start()
    // Let the close either fail or wait for the push in progress
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (closer.getState != Thread.State.TERMINATED && closer.getState != Thread.State.BLOCKED && System.nanoTime() < deadline) {
      Thread.sleep(1)
    }
    release.countDown()
    pusher.join(10000)
    closer.join(10000)

    assertThat(failure.get()).isNull()
    assertThat(completed.get()).isTrue
  }
}
