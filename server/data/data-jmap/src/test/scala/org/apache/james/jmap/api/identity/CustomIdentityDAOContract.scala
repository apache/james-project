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
 ****************************************************************/

package org.apache.james.jmap.api.identity

import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

trait CustomIdentityDAOContract {
  private val bob = Username.of("bob@localhost")

  def testee(): CustomIdentityDAO

  @Test
  def listShouldReturnEmptyWhenNone(): Unit = {
    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def listShouldReturnSavedIdentity(): Unit = {
    val identity = SMono(testee()
      .save(bob, IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .containsExactlyInAnyOrder(identity)
  }

  @Test
  def listShouldReturnSavedIdentities(): Unit = {
    val identity1 = SMono(testee()
      .save(bob, IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()
    val identity2 = SMono(testee()
      .save(bob, IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss2")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 3")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text 2 signature")),
        htmlSignature = Some(HtmlSignature("html 2 signature")))))
      .block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .containsExactlyInAnyOrder(identity1, identity2)
  }

  @Test
  def saveShouldReturnPersistedValues(): Unit = {
    val identity: Identity = SMono(testee().save(bob,
      IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()

    assertThat(identity)
      .isEqualTo(Identity(id = identity.id,
        name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")),
        mayDelete = MayDeleteIdentity(true)))
  }

  @Test
  def saveShouldNotReturnDeletedValues(): Unit = {
    val identity: Identity = SMono(testee().save(bob,
      IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()

    SMono(testee().delete(bob, Seq(identity.id))).block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def deleteShouldBeIdempotent(): Unit = {
    val identity: Identity = SMono(testee().save(bob,
      IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()

    SMono(testee().delete(bob, Seq(identity.id))).block()
    SMono(testee().delete(bob, Seq(identity.id))).block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def updateShouldModifyUnderlyingRecord(): Unit = {
    val identity: Identity = SMono(testee().save(bob,
      IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()

    SMono(testee().update(bob, identity.id, IdentityUpdateRequest(
      name = Some(IdentityNameUpdate(IdentityName("Bob (new name)"))),
      replyTo = Some(IdentityReplyToUpdate(Some(List(EmailAddress(Some(EmailerName("My Boss (updated)")), new MailAddress("boss-updated@domain.tld")))))),
      bcc = Some(IdentityBccUpdate(Some(List(EmailAddress(Some(EmailerName("My Boss 2 (updated)")), new MailAddress("boss-updated-2@domain.tld")))))),
      textSignature = Some(IdentityTextSignatureUpdate(Some(TextSignature("text 2 signature")))),
      htmlSignature = Some(IdentityHtmlSignatureUpdate(Some(HtmlSignature("html 2 signature")))))))
      .block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .containsExactlyInAnyOrder(Identity(id = identity.id,
        name = IdentityName("Bob (new name)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss (updated)")), new MailAddress("boss-updated@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2 (updated)")), new MailAddress("boss-updated-2@domain.tld")))),
        textSignature = Some(TextSignature("text 2 signature")),
        htmlSignature = Some(HtmlSignature("html 2 signature")),
        mayDelete = MayDeleteIdentity(true)))
  }

  @Test
  def partialUpdatesShouldBePossible(): Unit = {
    val identity: Identity = SMono(testee().save(bob,
      IdentityCreationRequest(name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()

    SMono(testee().update(bob, identity.id, IdentityUpdateRequest(
      name = Some(IdentityNameUpdate(IdentityName("Bob (new name)"))),
      replyTo = None,
      bcc = None,
      textSignature = None,
      htmlSignature = None)))
      .block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .containsExactlyInAnyOrder(Identity(id = identity.id,
        name = IdentityName("Bob (new name)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")),
        mayDelete = MayDeleteIdentity(true)))
  }

  @Test
  def updatingNotFoundIdentitiesShouldThrow(): Unit = {
    assertThatThrownBy(() => SMono(testee().update(bob, IdentityId.generate,
      IdentityUpdateRequest(
        name = Some(IdentityNameUpdate(IdentityName("Bob (new name)"))),
        replyTo = None,
        bcc = None,
        textSignature = None,
        htmlSignature = None)))
      .block())
      .isInstanceOf(classOf[IdentityNotFoundException])
  }
}
