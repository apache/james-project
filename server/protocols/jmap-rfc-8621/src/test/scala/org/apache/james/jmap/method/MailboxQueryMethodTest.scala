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

package org.apache.james.jmap.method

import java.util.stream.Stream

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{Invocation, QueryState}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.mailbox.{MailboxSession, MessageManager, Role, SystemMailboxesProvider}
import org.apache.james.metrics.api.MetricFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{Test, TestInstance}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments => TestArguments, MethodSource, ValueSource}
import org.mockito.Mockito.{mock, verify, verifyNoInteractions, when}
import play.api.libs.json.Json
import reactor.core.publisher.Flux

import scala.jdk.CollectionConverters._

@TestInstance(Lifecycle.PER_CLASS)
class MailboxQueryMethodTest {
  private val username = Username.of("bob@example.com")

  private def invoke(role: String, provider: SystemMailboxesProvider): InvocationWithContext = {
    val session = mock(classOf[MailboxSession])
    when(session.getUser).thenReturn(username)
    val method = new MailboxQueryMethod(provider, mock(classOf[MetricFactory]), null, null)
    val invocation = Invocation(MethodName("Mailbox/query"),
      Arguments(Json.obj("accountId" -> "account", "filter" -> Json.obj("role" -> role))), MethodCallId("q1"))
    val context = ProcessingContext(Map.empty, Map.empty)
    method.doProcess(Set.empty, InvocationWithContext(invocation, context), session,
      method.getRequest(session, invocation).toOption.get).block()
  }

  @ParameterizedTest
  @ValueSource(strings = Array("x-custom", "x-", "X-custom", "Invalid", "", "DRAFTS", " drafts "))
  def queryShouldReturnEmptyResponseWithoutConsultingStorageForUnknownRole(role: String): Unit = {
    val provider = mock(classOf[SystemMailboxesProvider])
    val result = invoke(role, provider)

    assertThat(result.invocation).isEqualTo(Invocation(MethodName("Mailbox/query"), Arguments(Json.obj(
      "accountId" -> "account", "queryState" -> QueryState.forMailboxIds(Seq.empty[MailboxId]).value,
      "canCalculateChanges" -> false, "ids" -> Json.arr(), "position" -> 0, "limit" -> 256)), MethodCallId("q1")))
    assertThat(result.processingContext).isEqualTo(ProcessingContext(Map.empty, Map.empty))
    verifyNoInteractions(provider)
  }

  def predefinedRoleCases(): Stream[TestArguments] =
    Seq(Role.INBOX -> "inbox", Role.DRAFTS -> "drafts", Role.OUTBOX -> "outbox", Role.SENT -> "sent",
      Role.TRASH -> "trash", Role.ARCHIVE -> "archive", Role.SPAM -> "junk", Role.TEMPLATES -> "templates",
      Role.RESTORED_MESSAGES -> "restored messages").flatMap { case (role, canonical) =>
      (Seq(canonical, role.getDefaultMailbox) ++ (if (role == Role.INBOX) Seq("Inbox", "InBoX") else Seq.empty))
        .distinct.map(input => TestArguments.of(input, role))
    }.asJava.stream()

  @ParameterizedTest
  @MethodSource(Array("predefinedRoleCases"))
  def queryShouldResolvePredefinedRole(input: String, role: Role): Unit = {
    val provider = mock(classOf[SystemMailboxesProvider])
    when(provider.getMailboxByRole(role, username)).thenReturn(Flux.empty[MessageManager]())
    val result = invoke(input, provider)

    assertThat((result.invocation.arguments.value \ "ids").get).isEqualTo(Json.arr())
    verify(provider).getMailboxByRole(role, username)
    assertThat(role.isSystemRole).isTrue()
    assertThat(role.getDefaultMailbox).isNotNull()
  }

  @Test
  def queryShouldPropagateStorageFailureForKnownRole(): Unit = {
    val provider = mock(classOf[SystemMailboxesProvider])
    val failure = new IllegalStateException("storage unavailable")
    when(provider.getMailboxByRole(Role.INBOX, username)).thenReturn(Flux.error[MessageManager](failure))

    assertThat(assertThrows(classOf[IllegalStateException], () => invoke("inbox", provider))).isSameAs(failure)
  }
}
