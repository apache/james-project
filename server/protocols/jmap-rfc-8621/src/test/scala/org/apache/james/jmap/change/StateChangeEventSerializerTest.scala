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
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.change.StateChangeEventSerializerTest.{EVENT, EVENT_EMPTY_TYPE_STATE_MAP, EVENT_ID, EVENT_JSON, EVENT_JSON_EMPTY_TYPE_STATE_MAP, EVENT_JSON_NO_DELIVERY, EVENT_NO_DELIVERY, USERNAME}
import org.apache.james.jmap.core.UuidState
import org.apache.james.json.JsonGenericSerializer
import org.apache.james.json.JsonGenericSerializer.UnknownTypeException
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

object StateChangeEventSerializerTest {
  val EVENT_ID: EventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4")
  val USERNAME: Username = Username.of("bob")
  val EVENT: StateChangeEvent = StateChangeEvent(eventId = EVENT_ID,
    username = USERNAME,
    map = Map(MailboxTypeName -> UuidState.INSTANCE,
      EmailTypeName -> UuidState.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943"),
      VacationResponseTypeName -> UuidState.fromStringUnchecked("2d9f1b12-3333-4444-5555-0106fb53a943"),
      EmailDeliveryTypeName -> UuidState.fromStringUnchecked("2d9f1b12-0000-1111-3333-0106fb53a943")))
  val EVENT_JSON: String =
    """{
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "typeStates": {
      |    "Mailbox": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |    "Email": "2d9f1b12-b35a-43e6-9af2-0106fb53a943",
      |    "EmailDelivery": "2d9f1b12-0000-1111-3333-0106fb53a943",
      |    "VacationResponse": "2d9f1b12-3333-4444-5555-0106fb53a943"
      |  },
      |  "type": "org.apache.james.jmap.change.StateChangeEvent"
      |}""".stripMargin
  val EVENT_NO_DELIVERY: StateChangeEvent = StateChangeEvent(eventId = EVENT_ID,
    username = USERNAME,
    map = Map(MailboxTypeName -> UuidState.INSTANCE,
      EmailTypeName -> UuidState.fromStringUnchecked("2d9f1b12-b35a-43e6-9af2-0106fb53a943")))
  val EVENT_JSON_NO_DELIVERY: String =
    """{
      |  "type": "org.apache.james.jmap.change.StateChangeEvent",
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "typeStates": {
      |    "Mailbox": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |    "Email": "2d9f1b12-b35a-43e6-9af2-0106fb53a943"
      |  }
      |}""".stripMargin

  val EVENT_EMPTY_TYPE_STATE_MAP: StateChangeEvent = StateChangeEvent(eventId = EVENT_ID,
    username = USERNAME,
    map = Map())
  val EVENT_JSON_EMPTY_TYPE_STATE_MAP: String =
    """{
      |  "type": "org.apache.james.jmap.change.StateChangeEvent",
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "typeStates": {
      |
      |  }
      |}""".stripMargin
}

class StateChangeEventSerializerTest {
  val typeNameSet: Set[TypeName] = Set(EmailTypeName, MailboxTypeName, ThreadTypeName, IdentityTypeName, EmailSubmissionTypeName, EmailDeliveryTypeName, VacationResponseTypeName)
  val typeStateFactory: TypeStateFactory = TypeStateFactory(typeNameSet.asJava)
  val stateChangeEventDTOFactory: StateChangeEventDTOFactory = StateChangeEventDTOFactory(typeStateFactory)

  @Test
  def shouldSerializeKnownEvent(): Unit =
    JsonSerializationVerifier.serializer(JsonGenericSerializer
      .forModules(stateChangeEventDTOFactory.dtoModule)
      .withoutNestedType())
      .bean(EVENT)
      .json(EVENT_JSON)
      .equalityTester((a, b) => {
        assertThat(a.eventId).isEqualTo(b.eventId)
        assertThat(a.username).isEqualTo(b.username)
        assertThat(a.asStateChange).isEqualTo(b.asStateChange)
      })
      .verify()

  @Test
  def shouldThrowWhenDeserializeUnknownEvent(): Unit =
    assertThatThrownBy(() =>
      JsonGenericSerializer
        .forModules(stateChangeEventDTOFactory.dtoModule)
        .withoutNestedType()
        .deserialize("""{
                       |	"eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
                       |	"username": "bob",
                       |	"typeStates": {
                       |		"Mailbox": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                       |		"Email": "2d9f1b12-b35a-43e6-9af2-0106fb53a943"
                       |	},
                       |	"type": "org.apache.james.jmap.change.Unknown"
                       |}""".stripMargin))
      .isInstanceOf(classOf[UnknownTypeException])

  @Test
  def shouldDeserializePreviousFormat(): Unit =
    assertThat(JsonGenericSerializer
        .forModules(stateChangeEventDTOFactory.dtoModule)
        .withoutNestedType()
        .deserialize("""{
                       |	"eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
                       |	"username": "bob",
                       |  "type": "org.apache.james.jmap.change.StateChangeEvent",
                       |  "emailState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                       |  "mailboxState": "2c9f1b12-aaaa-bbbb-cccc-0106fb53a943"
                       |}""".stripMargin))
      .isEqualTo(StateChangeEvent(eventId = EVENT_ID,
        username = USERNAME,
        map = Map(MailboxTypeName ->UuidState.fromStringUnchecked("2c9f1b12-aaaa-bbbb-cccc-0106fb53a943"),
          EmailTypeName -> UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"))))

  @Test
  def shouldDeserializeWhenAnOptionalFieldIsMissing(): Unit =
    assertThat(
      JsonGenericSerializer
        .forModules(stateChangeEventDTOFactory.dtoModule)
        .withoutNestedType()
        .deserialize(EVENT_JSON_NO_DELIVERY.stripMargin))
      .isEqualTo(EVENT_NO_DELIVERY)

  @Test
  def shouldDeserializeWhenTypeStateMapIsEmpty(): Unit =
    assertThat(
      JsonGenericSerializer
        .forModules(stateChangeEventDTOFactory.dtoModule)
        .withoutNestedType()
        .deserialize(EVENT_JSON_EMPTY_TYPE_STATE_MAP.stripMargin))
      .isEqualTo(EVENT_EMPTY_TYPE_STATE_MAP)
}