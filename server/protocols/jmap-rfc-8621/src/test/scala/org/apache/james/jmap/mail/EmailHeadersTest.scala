/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.mail

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.ZoneId

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.Properties
import org.apache.james.mime4j.dom.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmailHeadersTest {
  private val UTC: ZoneId = ZoneId.of("UTC")
  private val message: Message = Email.parseStreamAsMime4JMessage(new ByteArrayInputStream(
    ("Message-Id: <1@example.com>\r\n" +
      "In-Reply-To: <0@example.com>\r\n" +
      "References: <a@example.com> <0@example.com>\r\n" +
      "Date: 13 Oct 2026 10:00:00 +0000\r\n" +
      "From: alice@example.com\r\n" +
      "Sender: bob@example.com\r\n" +
      "To: carol@example.com\r\n" +
      "Cc: dave@example.com\r\n" +
      "Bcc: erin@example.com\r\n" +
      "Reply-To: frank@example.com\r\n" +
      "Subject: =?UTF-8?Q?caf=C3=A9?=\r\n" +
      "\r\n" +
      "body\r\n").getBytes(StandardCharsets.US_ASCII))).get

  @Test
  def allowedPropertiesShouldComputeEveryField(): Unit = {
    val headers = EmailHeaders.from(UTC, Email.allowedProperties)(message)

    assertThat(headers).isEqualTo(EmailHeaders.from(UTC)(message))
    assertThat(headers.headers.size).isEqualTo(11)
    assertThat(headers.references.value.map(_.size)).isEqualTo(Some(2))
    assertThat(headers.sender.isDefined).isTrue
    assertThat(headers.sentAt.isDefined).isTrue
    assertThat(headers.subject.isDefined).isTrue
    assertThat(headers.from.isDefined).isTrue
  }

  @Test
  def unrequestedFieldsShouldBeEmpty(): Unit = {
    val full = EmailHeaders.from(UTC)(message)
    val headers = EmailHeaders.from(UTC, Properties("id", "subject", "from"))(message)

    assertThat(headers).isEqualTo(EmailHeaders(
      headers = List(),
      messageId = MessageIdsHeaderValue(None),
      inReplyTo = MessageIdsHeaderValue(None),
      references = MessageIdsHeaderValue(None),
      to = None,
      cc = None,
      bcc = None,
      from = full.from,
      sender = None,
      replyTo = None,
      subject = full.subject,
      sentAt = None))
  }

  @Test
  def requestedFieldsShouldMatchTheFullComputation(): Unit = {
    val full = EmailHeaders.from(UTC)(message)
    val partial = EmailHeaders.from(UTC, Email.defaultProperties)(message)

    assertThat(partial).isEqualTo(full.copy(headers = List()))
  }
}
