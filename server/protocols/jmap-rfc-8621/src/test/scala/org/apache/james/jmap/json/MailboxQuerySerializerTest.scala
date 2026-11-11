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

package org.apache.james.jmap.json

import org.apache.james.jmap.mail.{NoRole, WithRole}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.Json

class MailboxQuerySerializerTest {
  @ParameterizedTest
  @ValueSource(strings = Array("inbox", "drafts", "outbox", "sent", "trash", "archive", "junk", "templates", "restored messages"))
  def deserializeShouldAcceptRoleNames(role: String): Unit = {
    val request = MailboxQuerySerializer.deserialize(Json.obj("accountId" -> "account", "filter" -> Json.obj("role" -> role)))

    assertThat(request.isSuccess).isTrue()
    assertThat(request.get.filter.role.asInstanceOf[WithRole].role.serialize()).isEqualTo(role)
  }

  @ParameterizedTest
  @ValueSource(strings = Array("Sent", "Spam", "Restored-Messages"))
  def deserializeShouldAcceptFolderNames(role: String): Unit = {
    val request = MailboxQuerySerializer.deserialize(Json.obj("accountId" -> "account", "filter" -> Json.obj("role" -> role)))

    assertThat(request.get.filter.role.asInstanceOf[WithRole].role.getDefaultMailbox).isEqualTo(role)
  }

  @ParameterizedTest
  @ValueSource(strings = Array("x-custom", "x-", "X-custom", "Invalid", "", "spam"))
  def deserializeShouldRejectUnknownRole(role: String): Unit = {
    val request = MailboxQuerySerializer.deserialize(Json.obj("accountId" -> "account", "filter" -> Json.obj("role" -> role)))

    assertThat(request.isError).isTrue()
  }

  @ParameterizedTest
  @ValueSource(strings = Array("123", "true", "{}", "[]"))
  def deserializeShouldRejectNonStringRole(role: String): Unit = {
    val request = MailboxQuerySerializer.deserialize(Json.obj("accountId" -> "account", "filter" -> Json.obj("role" -> Json.parse(role))))

    assertThat(request.isError).isTrue()
  }

  @Test
  def deserializeShouldAcceptNullRole(): Unit = {
    val request = MailboxQuerySerializer.deserialize(Json.parse("""{"accountId":"account","filter":{"role":null}}"""))

    assertThat(request.get.filter.role).isEqualTo(NoRole)
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    """{"accountId":"account"}""",
    """{"accountId":"account","filter":{}}""",
    """{"accountId":"account","filter":{"role":"inbox","unsupported":true}}"""))
  def deserializeShouldRejectMalformedRequest(request: String): Unit = {
    assertThat(MailboxQuerySerializer.deserialize(Json.parse(request)).isError).isTrue()
  }

  @Test
  def deserializeShouldPreserveQueryChangesValidation(): Unit = {
    assertThat(MailboxQuerySerializer.deserializeMailboxQueryChanges(Json.obj("accountId" -> "account")).isError).isTrue()
    assertThat(MailboxQuerySerializer.deserializeMailboxQueryChanges(Json.obj(
      "accountId" -> "account", "sinceQueryState" -> "00000000-0000-0000-0000-000000000000",
      "calculateTotal" -> false, "filter" -> Json.obj("role" -> "Inbox"))).isSuccess).isTrue()
  }
}
