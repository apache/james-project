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
import org.apache.james.jmap.core.{AccountId, OutboundMessage, PushState, StateChange, UuidState}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.EmitFailureHandler

class StateChangeListenerTest {
  private val mailboxState = UuidState.fromStringUnchecked("2f9f1b12-b35a-43e6-9af2-0106fb53a943")
  private val emailState = UuidState.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943")
  private val eventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4")

  @Test
  def reactiveEventShouldSendAnOutboundMessage(): Unit = {
    val sink: Sinks.Many[OutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val event = StateChangeEvent(eventId = eventId,
      username = Username.of("bob"),
      map = Map(MailboxTypeName -> mailboxState, EmailTypeName -> emailState))
    val listener = StateChangeListener(Set(MailboxTypeName, EmailTypeName), sink)

    listener.event(event)
    sink.emitComplete(EmitFailureHandler.FAIL_FAST)

    val globalState = PushState.from(mailboxState, emailState)
    assertThat(sink.asFlux().collectList().block())
      .containsExactly(StateChange(Map(AccountId.from(Username.of("bob")).toOption.get  -> TypeState(Map(
        MailboxTypeName -> mailboxState,
        EmailTypeName -> emailState))), Some(globalState)))
  }

  @Test
  def reactiveEventShouldOmitUnwantedTypes(): Unit = {
    val sink: Sinks.Many[OutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val event = StateChangeEvent(eventId = eventId,
      username = Username.of("bob"),
      map = Map(MailboxTypeName -> mailboxState, EmailTypeName -> emailState))
    val listener = StateChangeListener(Set(MailboxTypeName), sink)

    listener.event(event)
    sink.emitComplete(EmitFailureHandler.FAIL_FAST)

    val globalState = PushState.from(mailboxState, emailState)
    assertThat(sink.asFlux().collectList().block())
      .containsExactly(StateChange(Map(AccountId.from(Username.of("bob")).toOption.get -> TypeState(Map(
        MailboxTypeName -> mailboxState))), Some(globalState)))
  }

  @Test
  def reactiveEventShouldFilterOutUnwantedEvents(): Unit = {
    val sink: Sinks.Many[OutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val event = StateChangeEvent(eventId = eventId,
      username = Username.of("bob"),
      map = Map(EmailTypeName -> emailState))
    val listener = StateChangeListener(Set(MailboxTypeName), sink)

    listener.event(event)
    sink.emitComplete(EmitFailureHandler.FAIL_FAST)

    assertThat(sink.asFlux().collectList().block())
      .isEmpty()
  }
}
