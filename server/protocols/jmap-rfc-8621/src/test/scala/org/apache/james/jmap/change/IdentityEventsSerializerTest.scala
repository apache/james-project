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

package org.apache.james.jmap.change

import java.util.UUID

import org.apache.james.JsonSerializationVerifier
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.change.IdentityEventsSerializerTest._
import org.apache.james.jmap.api.identity.{AllCustomIdentitiesDeleted, CustomIdentityCreated, CustomIdentityDeleted, CustomIdentityUpdated}
import org.apache.james.json.JsonGenericSerializer
import org.apache.james.json.JsonGenericSerializer.UnknownTypeException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

object IdentityEventsSerializerTest {
  val EVENT_ID: EventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4")
  val USERNAME: Username = Username.of("bob")
  val IDENTITY_ID: IdentityId = IdentityId(UUID.fromString("2c9f1b12-b35a-43e6-9af2-0106fb53a943"))
  val IDENTITY_ID_2: IdentityId = IdentityId(UUID.fromString("3d9f1b12-b35a-43e6-9af2-0106fb53a943"))

  val IDENTITY: Identity = Identity(
    id = IDENTITY_ID,
    sortOrder = 100,
    name = IdentityName("Bob"),
    email = new MailAddress("bob@example.com"),
    replyTo = Some(List(EmailAddress(None, new MailAddress("reply@example.com")))),
    bcc = Some(List(EmailAddress(Some(EmailerName("Admin")), new MailAddress("admin@example.com")))),
    textSignature = TextSignature("Best regards"),
    htmlSignature = HtmlSignature("<b>Best regards</b>"),
    mayDelete = MayDeleteIdentity(true))

  val IDENTITY_NO_OPTIONAL: Identity = Identity(
    id = IDENTITY_ID,
    sortOrder = 100,
    name = IdentityName("Bob"),
    email = new MailAddress("bob@example.com"),
    replyTo = None,
    bcc = None,
    textSignature = TextSignature(""),
    htmlSignature = HtmlSignature(""),
    mayDelete = MayDeleteIdentity(false))

  val CREATED_EVENT: CustomIdentityCreated = CustomIdentityCreated(EVENT_ID, USERNAME, IDENTITY)
  val CREATED_EVENT_JSON: String =
    """{
      |  "type": "org.apache.james.jmap.api.identity.CustomIdentityCreated",
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "identity": {
      |    "id": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |    "sortOrder": 100,
      |    "name": "Bob",
      |    "email": "bob@example.com",
      |    "replyTo": [{"email": "reply@example.com"}],
      |    "bcc": [{"name": "Admin", "email": "admin@example.com"}],
      |    "textSignature": "Best regards",
      |    "htmlSignature": "<b>Best regards</b>",
      |    "mayDelete": true
      |  }
      |}""".stripMargin

  val CREATED_EVENT_NO_OPTIONAL: CustomIdentityCreated = CustomIdentityCreated(EVENT_ID, USERNAME, IDENTITY_NO_OPTIONAL)
  val CREATED_EVENT_NO_OPTIONAL_JSON: String =
    """{
      |  "type": "org.apache.james.jmap.api.identity.CustomIdentityCreated",
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "identity": {
      |    "id": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |    "sortOrder": 100,
      |    "name": "Bob",
      |    "email": "bob@example.com",
      |    "textSignature": "",
      |    "htmlSignature": "",
      |    "mayDelete": false
      |  }
      |}""".stripMargin

  val UPDATED_EVENT: CustomIdentityUpdated = CustomIdentityUpdated(EVENT_ID, USERNAME, IDENTITY)
  val UPDATED_EVENT_JSON: String =
    """{
      |  "type": "org.apache.james.jmap.api.identity.CustomIdentityUpdated",
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "identity": {
      |    "id": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |    "sortOrder": 100,
      |    "name": "Bob",
      |    "email": "bob@example.com",
      |    "replyTo": [{"email": "reply@example.com"}],
      |    "bcc": [{"name": "Admin", "email": "admin@example.com"}],
      |    "textSignature": "Best regards",
      |    "htmlSignature": "<b>Best regards</b>",
      |    "mayDelete": true
      |  }
      |}""".stripMargin

  val DELETED_EVENT: CustomIdentityDeleted = CustomIdentityDeleted(EVENT_ID, USERNAME, Set(IDENTITY_ID))
  val DELETED_EVENT_JSON: String =
    """{
      |  "type": "org.apache.james.jmap.api.identity.CustomIdentityDeleted",
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "identityIds": ["2c9f1b12-b35a-43e6-9af2-0106fb53a943"]
      |}""".stripMargin

  val ALL_DELETED_EVENT: AllCustomIdentitiesDeleted = AllCustomIdentitiesDeleted(EVENT_ID, USERNAME, Set(IDENTITY_ID))
  val ALL_DELETED_EVENT_JSON: String =
    """{
      |  "type": "org.apache.james.jmap.api.identity.AllCustomIdentitiesDeleted",
      |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
      |  "username": "bob",
      |  "identityIds": ["2c9f1b12-b35a-43e6-9af2-0106fb53a943"]
      |}""".stripMargin
}

class IdentityEventsSerializerTest {
  @Test
  def shouldSerializeCustomIdentityCreated(): Unit =
    JsonSerializationVerifier.dtoModule(IdentityEventsSerializer.customIdentityCreatedModule)
      .bean(CREATED_EVENT)
      .json(CREATED_EVENT_JSON)
      .verify()

  @Test
  def shouldSerializeCustomIdentityCreatedWithoutOptionalFields(): Unit =
    JsonSerializationVerifier.dtoModule(IdentityEventsSerializer.customIdentityCreatedModule)
      .bean(CREATED_EVENT_NO_OPTIONAL)
      .json(CREATED_EVENT_NO_OPTIONAL_JSON)
      .verify()

  @Test
  def shouldSerializeCustomIdentityUpdated(): Unit =
    JsonSerializationVerifier.dtoModule(IdentityEventsSerializer.customIdentityUpdatedModule)
      .bean(UPDATED_EVENT)
      .json(UPDATED_EVENT_JSON)
      .verify()

  @Test
  def shouldSerializeCustomIdentityDeleted(): Unit =
    JsonSerializationVerifier.dtoModule(IdentityEventsSerializer.customIdentityDeletedModule)
      .bean(DELETED_EVENT)
      .json(DELETED_EVENT_JSON)
      .verify()

  @Test
  def shouldSerializeAllCustomIdentitiesDeleted(): Unit =
    JsonSerializationVerifier.dtoModule(IdentityEventsSerializer.allCustomIdentitiesDeletedModule)
      .bean(ALL_DELETED_EVENT)
      .json(ALL_DELETED_EVENT_JSON)
      .verify()

  @Test
  def shouldThrowWhenDeserializeUnknownEventType(): Unit =
    assertThatThrownBy(() =>
      JsonGenericSerializer
        .forModules(IdentityEventsSerializer.customIdentityCreatedModule)
        .withoutNestedType()
        .deserialize(
          """{
            |  "type": "org.apache.james.jmap.api.identity.UnknownEvent",
            |  "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
            |  "username": "bob",
            |  "identityIds": []
            |}""".stripMargin))
      .isInstanceOf(classOf[UnknownTypeException])
}
