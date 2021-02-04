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

package org.apache.james.jmap.change

import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.core.{AccountId, State, StateChange, WebSocketOutboundMessage}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.EmitFailureHandler
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers

class StateChangeListenerTest {
  private val mailboxState = State.fromStringUnchecked("2f9f1b12-b35a-43e6-9af2-0106fb53a943")
  private val emailState = State.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943")
  private val eventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4")

  @Test
  def reactiveEventShouldSendAnOutboundMessage(): Unit = {
    val sink: Sinks.Many[WebSocketOutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val event = StateChangeEvent(eventId = eventId,
      username = Username.of("bob"),
      mailboxState = Some(mailboxState),
      emailState = Some(emailState))
    val listener = StateChangeListener(Set(MailboxTypeName, EmailTypeName), sink)

    SMono(listener.reactiveEvent(event)).subscribeOn(Schedulers.elastic()).block()
    sink.emitComplete(EmitFailureHandler.FAIL_FAST)

    assertThat(sink.asFlux().collectList().block())
      .containsExactly(StateChange(Map(AccountId.from(Username.of("bob")).toOption.get  -> TypeState(Map(
        MailboxTypeName -> mailboxState,
        EmailTypeName -> emailState)))))
  }

  @Test
  def reactiveEventShouldOmitUnwantedTypes(): Unit = {
    val sink: Sinks.Many[WebSocketOutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val event = StateChangeEvent(eventId = eventId,
      username = Username.of("bob"),
      mailboxState = Some(mailboxState),
      emailState = Some(emailState))
    val listener = StateChangeListener(Set(MailboxTypeName), sink)

    SMono(listener.reactiveEvent(event)).subscribeOn(Schedulers.elastic()).block()
    sink.emitComplete(EmitFailureHandler.FAIL_FAST)

    assertThat(sink.asFlux().collectList().block())
      .containsExactly(StateChange(Map(AccountId.from(Username.of("bob")).toOption.get -> TypeState(Map(
        MailboxTypeName -> mailboxState)))))
  }

  @Test
  def reactiveEventShouldFilterOutUnwantedEvents(): Unit = {
    val sink: Sinks.Many[WebSocketOutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val event = StateChangeEvent(eventId = eventId,
      username = Username.of("bob"),
      mailboxState = None,
      emailState = Some(emailState))
    val listener = StateChangeListener(Set(MailboxTypeName), sink)

    SMono(listener.reactiveEvent(event)).subscribeOn(Schedulers.elastic()).block()
    sink.emitComplete(EmitFailureHandler.FAIL_FAST)

    assertThat(sink.asFlux().collectList().block())
      .isEmpty()
  }
}
