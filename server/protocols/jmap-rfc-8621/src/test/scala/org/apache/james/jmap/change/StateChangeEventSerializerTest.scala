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

import org.apache.james.JsonSerializationVerifier
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.change.StateChangeEventSerializerTest.{EVENT, EVENT_JSON, EVENT_JSON_NO_DELIVERY, EVENT_NO_DELIVERY}
import org.apache.james.jmap.core.State
import org.apache.james.json.JsonGenericSerializer
import org.apache.james.json.JsonGenericSerializer.UnknownTypeException
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test


object StateChangeEventSerializerTest {
  val EVENT_ID: EventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4")
  val USERNAME: Username = Username.of("bob")
  val EVENT: StateChangeEvent = StateChangeEvent(eventId = EVENT_ID,
    username = USERNAME,
    mailboxState = Some(State.INSTANCE),
    emailState = Some(State.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943")),
    vacationResponseState = Some(State.fromStringUnchecked("2d9f1b12-3333-4444-5555-0106fb53a943")),
    emailDeliveryState = Some(State.fromStringUnchecked("2d9f1b12-0000-1111-3333-0106fb53a943")))
  val EVENT_JSON: String =
    """{
      |  "eventId":"6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username":"bob",
      |  "mailboxState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |  "emailState":"2d9f1b12-b35a-43e6-9af2-0106fb53a943",
      |  "emailDeliveryState":"2d9f1b12-0000-1111-3333-0106fb53a943",
      |  "vacationResponseState":"2d9f1b12-3333-4444-5555-0106fb53a943",
      |  "type":"org.apache.james.jmap.change.StateChangeEvent"
      |}""".stripMargin
  val EVENT_NO_DELIVERY: StateChangeEvent = StateChangeEvent(eventId = EVENT_ID,
    username = USERNAME,
    mailboxState = Some(State.INSTANCE),
    emailState = Some(State.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943")),
    emailDeliveryState = None,
    vacationResponseState = None)
  val EVENT_JSON_NO_DELIVERY: String =
    """{
      |  "eventId":"6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username":"bob",
      |  "mailboxState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |  "emailState":"2d9f1b12-b35a-43e6-9af2-0106fb53a943",
      |  "type":"org.apache.james.jmap.change.StateChangeEvent"
      |}""".stripMargin
}

class StateChangeEventSerializerTest {
  @Test
  def shouldSerializeKnownEvent(): Unit =
    JsonSerializationVerifier.serializer(JsonGenericSerializer
      .forModules(StateChangeEventDTO.dtoModule)
      .withoutNestedType())
      .bean(EVENT)
      .json(EVENT_JSON)
      .verify()

  @Test
  def shouldThrowWhenDeserializeUnknownEvent(): Unit =
    assertThatThrownBy(() =>
      JsonGenericSerializer
        .forModules(StateChangeEventDTO.dtoModule)
        .withoutNestedType()
        .deserialize("""{
                       |  "eventId":"6e0dd59d-660e-4d9b-b22f-0354479f47b4",
                       |  "username":"bob",
                       |  "mailboxState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                       |  "emailState":"2d9f1b12-b35a-43e6-9af2-0106fb53a943",
                       |  "type":"org.apache.james.jmap.change.Unknown"
                       |}""".stripMargin))
      .isInstanceOf(classOf[UnknownTypeException])

  @Test
  def shouldDeserializeWhenAnOptionalFieldIsMissing(): Unit =
    assertThat(
      JsonGenericSerializer
        .forModules(StateChangeEventDTO.dtoModule)
        .withoutNestedType()
        .deserialize(EVENT_JSON_NO_DELIVERY.stripMargin))
      .isEqualTo(EVENT_NO_DELIVERY)
}