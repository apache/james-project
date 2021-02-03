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

import java.nio.charset.Charset

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.core.State
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, verify, when}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono
import reactor.netty.NettyOutbound
import reactor.netty.http.websocket.WebsocketOutbound

class StateChangeListenerTest {
  private val outbound: WebsocketOutbound = mock(classOf[WebsocketOutbound])

  @Test
  def reactiveEventShouldSendAnOutboundMessage(): Unit = {
    val event = StateChangeEvent(eventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4"),
      username = Username.of("bob"),
      mailboxState = Some(State.fromStringUnchecked("2f9f1b12-b35a-43e6-9af2-0106fb53a943")),
      emailState = Some(State.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943")))
    val listener = StateChangeListener(outbound)
    val nettyOutbound = mock(classOf[NettyOutbound])
    when(outbound.sendString(any(), any())).thenReturn(nettyOutbound)

    listener.reactiveEvent(event)

    val captor: ArgumentCaptor[Publisher[String]] = ArgumentCaptor.forClass(classOf[Publisher[String]])
    verify(outbound).sendString(captor.capture(), any(classOf[Charset]))
    assertThatJson(SMono(captor.getValue).block()).isEqualTo(
      """
        |{
        |  "@type":"StateChange",
        |  "changed":{
        |    "81b637d8fcd2c6da6359e6963113a1170de795e4b725b84d1e0b4cfd9ec58ce9":{
        |      "Mailbox":"2f9f1b12-b35a-43e6-9af2-0106fb53a943",
        |      "Email":"2d9f1b12-b35a-43e6-9af2-0106fb53a943"
        |    }
        |  }
        |}
        |""".stripMargin)
  }
}
