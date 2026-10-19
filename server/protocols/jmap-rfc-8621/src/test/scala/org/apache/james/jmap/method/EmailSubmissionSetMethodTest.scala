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

import eu.timepit.refined.auto._
import jakarta.mail.Session
import jakarta.mail.internet.{InternetAddress, MimeMessage}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.mail.EmailSubmissionCreationId
import org.apache.james.jmap.method.EmailSubmissionSetMethod.CreationFailure
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class EmailSubmissionSetMethodTest {
  @Test
  def shouldReportForbiddenHeaderProperties(): Unit = {
    val error = CreationFailure(EmailSubmissionCreationId("c1"),
      ForbiddenFromException(List("other@example.com"), Properties("from", "sender"))).asSetError

    assertThat(error).isEqualTo(SetError("forbiddenFrom",
      SetErrorDescription("Attempt to send a mail whose MimeMessage From and Sender fields not allowed for connected user: List(other@example.com)"),
      Some(Properties("from", "sender"))))
  }

  @Test
  def shouldReportForbiddenEnvelope(): Unit = {
    val error = CreationFailure(EmailSubmissionCreationId("c1"), ForbiddenMailFromException("other@example.com")).asSetError

    assertThat(error).isEqualTo(SetError("forbiddenMailFrom",
      SetErrorDescription("Attempt to send a mail whose envelope From not allowed for connected user: other@example.com"),
      Some(Properties("envelope.mailFrom"))))
  }

  @Test
  def shouldReportMissingFromAsInvalidEmail(): Unit = {
    val error = CreationFailure(EmailSubmissionCreationId("c1"), MissingFromHeaderException()).asSetError

    assertThat(error).isEqualTo(SetError("invalidEmail",
      SetErrorDescription("Attempt to send a mail whose MimeMessage From is missing"), Some(Properties("from"))))
  }

  @Test
  def shouldPreserveNoRecipients(): Unit = {
    val error = CreationFailure(EmailSubmissionCreationId("c1"), NoRecipientException()).asSetError

    assertThat(error).isEqualTo(SetError("noRecipients", SetErrorDescription("Attempt to send a mail with no recipients"), None))
  }

  @Test
  def shouldRejectMissingFrom(): Unit = {
    val message = new MimeMessage(Session.getInstance(new java.util.Properties()))

    assertThatThrownBy(() => EmailSubmissionSetMethod.validateFromHeader(message).block())
      .hasCauseInstanceOf(classOf[MissingFromHeaderException])
  }

  @Test
  def shouldRejectEmptyFrom(): Unit = {
    val message = new MimeMessage(Session.getInstance(new java.util.Properties()))
    message.setHeader("From", "")

    assertThatThrownBy(() => EmailSubmissionSetMethod.validateFromHeader(message).block())
      .hasCauseInstanceOf(classOf[MissingFromHeaderException])
  }

  @Test
  def shouldNotUseSenderAsMissingFrom(): Unit = {
    val message = new MimeMessage(Session.getInstance(new java.util.Properties()))
    message.setSender(new InternetAddress("alice@example.com"))

    assertThatThrownBy(() => EmailSubmissionSetMethod.validateFromHeader(message).block())
      .hasCauseInstanceOf(classOf[MissingFromHeaderException])
  }

  @Test
  def shouldAcceptFromWithoutSender(): Unit = {
    val message = new MimeMessage(Session.getInstance(new java.util.Properties()))
    message.setFrom(new InternetAddress("alice@example.com"))

    assertThat(EmailSubmissionSetMethod.validateFromHeader(message).block()).isSameAs(message)
  }
}
