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

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.{Locale, Optional, Properties => JavaProperties}

import eu.timepit.refined.auto._
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.apache.james.core.{Domain, Username}
import org.apache.james.domainlist.api.DomainList
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.lifecycle.api.LifecycleUtil
import org.apache.james.mailbox.{MailboxSession, MailboxSessionUtil}
import org.apache.james.server.core.MailImpl
import org.apache.james.user.memory.MemoryUsersRepository
import org.apache.mailet.Mail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.{mock, when}

class SenderHeaderEmailSubmissionSetValidationTest {
  private val BOB: Username = Username.of("bob@domain.tld")
  private val ALICE: Username = Username.of("alice@domain.tld")
  private val domainList: DomainList = {
    val domainList = mock(classOf[DomainList])
    when(domainList.containsDomain(Domain.of("domain.tld"))).thenReturn(true)
    domainList
  }

  private val testee = new SenderHeaderEmailSubmissionSetValidation(MemoryUsersRepository.withVirtualHosting(domainList), domainList)

  private def mailWithHeaders(senderFields: String*): Mail = {
    val headers = senderFields.map(value => s"Sender: $value\r\n").mkString
    val raw = s"From: bob@domain.tld\r\n${headers}To: andre@domain.tld\r\nSubject: test\r\n\r\nbody\r\n"
    val message = new MimeMessage(Session.getDefaultInstance(new JavaProperties()),
      new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)))
    MailImpl.builder()
      .name("mail")
      .sender("bob@domain.tld")
      .addRecipient("andre@domain.tld")
      .mimeMessage(message)
      .build()
  }

  private def validate(session: MailboxSession, senderFields: String*): Option[SetError] = {
    val mail = mailWithHeaders(senderFields: _*)
    try {
      testee.validate(mail, session).block()
    } finally {
      LifecycleUtil.dispose(mail)
    }
  }

  private def validateAsBob(senderFields: String*): Option[SetError] =
    validate(MailboxSessionUtil.create(BOB), senderFields: _*)

  private def delegatedSession(accountOwner: Username, loggedInUser: Username): MailboxSession =
    new MailboxSession(MailboxSession.SessionId.of(42), accountOwner, Optional.of(loggedInUser),
      new java.util.ArrayList[Locale](), '.', MailboxSession.SessionType.System)

  private def assertRejected(result: Option[SetError]): Unit = {
    assertThat(result.isDefined).as("rejected").isTrue
    assertThat(result.get.`type`).isEqualTo(EmailSubmissionSetMethod.forbiddenFrom)
    assertThat(result.get.properties).isEqualTo(Some(Properties("sender")))
  }

  private def assertAccepted(result: Option[SetError]): Unit =
    assertThat(result.isEmpty).as(s"accepted but got $result").isTrue

  @Test
  def shouldAcceptMailWithoutSender(): Unit =
    assertAccepted(validateAsBob())

  @Test
  def shouldAcceptSenderBeingTheLoggedInUser(): Unit =
    assertAccepted(validateAsBob("bob@domain.tld"))

  @Test
  def shouldAcceptSenderWithADisplayName(): Unit =
    assertAccepted(validateAsBob("Bob <bob@domain.tld>"))

  @Test
  def shouldAcceptSenderWithAQuotedDisplayNameContainingAComma(): Unit =
    assertAccepted(validateAsBob("\"Doe, Bob\" <bob@domain.tld>"))

  @Test
  def shouldAcceptSenderWithAnEncodedWordDisplayName(): Unit =
    assertAccepted(validateAsBob("=?UTF-8?Q?B=C3=B6b?= <bob@domain.tld>"))

  @Test
  def shouldAcceptFoldedSender(): Unit =
    assertAccepted(validateAsBob("Bob\r\n <bob@domain.tld>"))

  @Test
  def shouldIgnoreCase(): Unit =
    assertAccepted(validateAsBob("BOB@Domain.TLD"))

  @Test
  def shouldAcceptASubAddressOfTheLoggedInUser(): Unit =
    assertAccepted(validateAsBob("bob+news@domain.tld"))

  @Test
  def shouldRejectAnotherUser(): Unit =
    assertThat(validateAsBob("alice@domain.tld"))
      .isEqualTo(Some(SetError(EmailSubmissionSetMethod.forbiddenFrom,
        SetErrorDescription("Attempt to send a mail whose MimeMessage Sender field does not match the logged in user bob@domain.tld"),
        Some(Properties("sender")))))

  @Test
  def shouldRejectAnotherAddressAfterTheLoggedInUser(): Unit =
    assertRejected(validateAsBob("bob@domain.tld, alice@domain.tld"))

  @Test
  def shouldRejectSeveralAddressesEvenWhenAllAreTheLoggedInUser(): Unit =
    assertRejected(validateAsBob("bob@domain.tld, Bob <bob@domain.tld>"))

  @Test
  def shouldRejectASecondSenderFieldNotBeingTheLoggedInUser(): Unit =
    assertRejected(validateAsBob("bob@domain.tld", "alice@domain.tld"))

  @Test
  def shouldRejectAGroup(): Unit =
    assertRejected(validateAsBob("team: bob@domain.tld;"))

  @Test
  def shouldRejectAGroupNamedLikeAHeader(): Unit =
    assertRejected(validateAsBob("To: bob@domain.tld;"))

  @Test
  def shouldRejectAnUnterminatedComment(): Unit =
    assertRejected(validateAsBob("bob@domain.tld ("))

  @Test
  def shouldAcceptATrailingComment(): Unit =
    assertAccepted(validateAsBob("bob@domain.tld (Bob)"))

  @Test
  def shouldRejectAnAddressAfterAnAngleAddress(): Unit =
    assertRejected(validateAsBob("<bob@domain.tld> alice@domain.tld"))

  @Test
  def shouldRejectAnAddressAfterADisplayNameAndAngleAddress(): Unit =
    assertRejected(validateAsBob("Bob <bob@domain.tld> alice@domain.tld"))

  @Test
  def shouldRejectAnAddressAfterAComment(): Unit =
    assertRejected(validateAsBob("bob@domain.tld (comment) alice@domain.tld"))

  @Test
  def shouldRejectATrailingComma(): Unit =
    assertRejected(validateAsBob("Bob <bob@domain.tld>,"))

  @Test
  def shouldRejectACommentWithinTheDomain(): Unit =
    assertRejected(validateAsBob("bob@domain.tld(comment).evil"))

  @Test
  def shouldRejectAnEmptyGroup(): Unit =
    assertRejected(validateAsBob("undisclosed-recipients:;"))

  @Test
  def shouldRejectAnEmptySender(): Unit =
    assertRejected(validateAsBob(""))

  @Test
  def shouldRejectAnUnparsableSender(): Unit =
    assertRejected(validateAsBob("<bob@domain.tld"))

  @Test
  def shouldRejectAnAddressWithoutDomain(): Unit =
    assertRejected(validateAsBob("bob"))

  @Test
  def shouldRejectAQuotedLocalPartHoldingAnAt(): Unit =
    assertRejected(validateAsBob("\"bob@domain.tld\"@domain.tld"))

  @Test
  def shouldRejectARemoteDomain(): Unit =
    assertRejected(validateAsBob("bob@remote.tld"))

  @Test
  def shouldCheckTheDomainWithoutVirtualHosting(): Unit = {
    val withoutVirtualHosting = new SenderHeaderEmailSubmissionSetValidation(MemoryUsersRepository.withoutVirtualHosting(domainList), domainList)
    val session = MailboxSessionUtil.create(Username.of("bob"))
    val mail = mailWithHeaders("bob@remote.tld")
    try {
      assertRejected(withoutVirtualHosting.validate(mail, session).block())
    } finally {
      LifecycleUtil.dispose(mail)
    }
  }

  @Test
  def shouldAcceptALocalDomainWithoutVirtualHosting(): Unit = {
    val withoutVirtualHosting = new SenderHeaderEmailSubmissionSetValidation(MemoryUsersRepository.withoutVirtualHosting(domainList), domainList)
    val session = MailboxSessionUtil.create(Username.of("bob"))
    val mail = mailWithHeaders("bob@domain.tld")
    try {
      assertAccepted(withoutVirtualHosting.validate(mail, session).block())
    } finally {
      LifecycleUtil.dispose(mail)
    }
  }

  @Test
  def shouldCompareWithTheLoggedInUserUponDelegation(): Unit =
    assertAccepted(validate(delegatedSession(accountOwner = BOB, loggedInUser = ALICE), "alice@domain.tld"))

  @Test
  def shouldRejectTheAccountOwnerUponDelegation(): Unit =
    assertRejected(validate(delegatedSession(accountOwner = BOB, loggedInUser = ALICE), "bob@domain.tld"))

  @Test
  def shouldCompareWithTheUserWhenNoLoggedInUser(): Unit = {
    val systemSession = new MailboxSession(MailboxSession.SessionId.of(42), BOB, Optional.empty[Username](),
      new java.util.ArrayList[Locale](), '.', MailboxSession.SessionType.System)

    assertAccepted(validate(systemSession, "bob@domain.tld"))
  }
}
