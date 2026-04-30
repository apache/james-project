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

import java.util.concurrent.CopyOnWriteArrayList

import org.apache.james.core.{MailAddress, Username}
import org.apache.james.events.EventListener.{ExecutionMode, ReactiveGroupEventListener}
import org.apache.james.events.{Event, EventBus, Group}
import org.apache.james.jmap.api.identity.CustomIdentityDAOContract.{CREATION_REQUEST, bob}
import org.apache.james.jmap.api.identity.IdentityRepositoryTest.{BOB, IDENTITY1}
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object CustomIdentityDAOContract {
  val bob: Username = Username.of("bob@localhost")
  val CREATION_REQUEST: IdentityCreationRequest = IdentityCreationRequest(name = Some(IdentityName("Bob (custom address)")),
    email = bob.asMailAddress(),
    replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
    bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
    textSignature = Some(TextSignature("text signature")),
    htmlSignature = Some(HtmlSignature("html signature")))

  class IdentityEventCollectorGroup extends Group {}

  class IdentityEventCollector extends ReactiveGroupEventListener {
    val events: CopyOnWriteArrayList[IdentityEvent] = new CopyOnWriteArrayList[IdentityEvent]()

    override def getDefaultGroup: Group = new IdentityEventCollectorGroup()

    override def isHandling(event: Event): Boolean = event.isInstanceOf[IdentityEvent]

    override def getExecutionMode: ExecutionMode = ExecutionMode.SYNCHRONOUS

    override def reactiveEvent(event: Event): Publisher[Void] = {
      events.add(event.asInstanceOf[IdentityEvent])
      SMono.empty[Void]
    }
  }
}

trait CustomIdentityDAOContract {

  def testee(): CustomIdentityDAO

  def eventBus: EventBus

  @Test
  def listShouldReturnEmptyWhenNone(): Unit = {
    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def listShouldReturnSavedIdentity(): Unit = {
    val identity: Identity = SMono(testee()
      .save(bob, CREATION_REQUEST))
      .block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .containsExactlyInAnyOrder(identity)
  }

  @Test
  def listShouldReturnSavedIdentities(): Unit = {
    val identity1: Identity = SMono(testee()
      .save(bob, CREATION_REQUEST))
      .block()
    val identity2: Identity = SMono(testee()
      .save(bob, IdentityCreationRequest(name = Some(IdentityName("Bob (custom address)")),
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
  def findByIdentityIdShouldReturnEmptyWhenNotFound() : Unit = {
    assertThat(SMono(testee().findByIdentityId(bob, IdentityId.generate)).blockOption().toJava)
      .isEmpty
  }

  @Test
  def findByIdentityIdShouldReturnEntry() : Unit = {
    val identity1: Identity = SMono(testee()
      .save(bob, IdentityCreationRequest(name = Some(IdentityName("Bob (custom address)")),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()

    assertThat(SMono(testee().findByIdentityId(bob, identity1.id)).block())
      .isEqualTo(identity1)
  }

  @Test
  def saveShouldReturnPersistedValues(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()

    assertThat(identity)
      .isEqualTo(Identity(id = identity.id,
        name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = TextSignature("text signature"),
        htmlSignature = HtmlSignature("html signature"),
        mayDelete = MayDeleteIdentity(true)))
  }

  @Test
  def saveShouldNotReturnDeletedValues(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()

    SMono(testee().delete(bob, Set(identity.id))).block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def saveShouldNotReturnDeletedAllValues(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()

    SMono(testee().delete(bob)).block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def saveShouldDefineDefaultValuesInCaseSomePropertiesEmpty(): Unit = {
    val identity: Identity = SMono(testee().save(bob,
      IdentityCreationRequest(name = None,
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = None,
        htmlSignature = None)))
      .block()

    assertThat(identity)
      .isEqualTo(Identity(id = identity.id,
        name = IdentityName(""),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = TextSignature(""),
        htmlSignature = HtmlSignature(""),
        mayDelete = MayDeleteIdentity(true)))
  }

  @Test
  def deleteShouldBeIdempotent(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()

    SMono(testee().delete(bob, Set(identity.id))).block()
    SMono(testee().delete(bob, Set(identity.id))).block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def deleteAllShouldBeIdempotent(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()

    SMono(testee().delete(bob)).block()
    SMono(testee().delete(bob)).block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def updateShouldModifyUnderlyingRecord(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()

    SMono(testee().update(bob, identity.id, IdentityUpdateRequest(
      name = Some(IdentityNameUpdate(IdentityName("Bob (new name)"))),
      replyTo = Some(IdentityReplyToUpdate(Some(List(EmailAddress(Some(EmailerName("My Boss (updated)")), new MailAddress("boss-updated@domain.tld")))))),
      bcc = Some(IdentityBccUpdate(Some(List(EmailAddress(Some(EmailerName("My Boss 2 (updated)")), new MailAddress("boss-updated-2@domain.tld")))))),
      textSignature = Some(IdentityTextSignatureUpdate(TextSignature("text 2 signature"))),
      htmlSignature = Some(IdentityHtmlSignatureUpdate(HtmlSignature("html 2 signature"))))))
      .block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .containsExactlyInAnyOrder(Identity(id = identity.id,
        name = IdentityName("Bob (new name)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss (updated)")), new MailAddress("boss-updated@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2 (updated)")), new MailAddress("boss-updated-2@domain.tld")))),
        textSignature = TextSignature("text 2 signature"),
        htmlSignature = HtmlSignature("html 2 signature"),
        mayDelete = MayDeleteIdentity(true)))
  }

  @Test
  def partialUpdatesShouldBePossible(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
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
        textSignature = TextSignature("text signature"),
        htmlSignature = HtmlSignature("html signature"),
        mayDelete = MayDeleteIdentity(true)))
  }

  @Test
  def sortOrderUpdatesShouldBePossible(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()

    SMono(testee().update(bob, identity.id, IdentityUpdateRequest(
      sortOrder = Some(IdentitySortOrderUpdate(354)))))
      .block()

    assertThat(SFlux(testee().list(bob)).asJava().collectList().block())
      .containsExactlyInAnyOrder(Identity(id = identity.id,
        name = IdentityName("Bob (custom address)"),
        email = bob.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = TextSignature("text signature"),
        htmlSignature = HtmlSignature("html signature"),
        sortOrder = 354,
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

  @Test
  def upsertShouldUpdateExistsEntry(): Unit = {
    val identity: Identity = SMono(testee().save(bob, CREATION_REQUEST))
      .block()
    val updateIdentity: Identity = identity.copy(name = IdentityName("Bob (custom address)"))
    SMono(testee().upsert(bob, updateIdentity)).block()
    assertThat(SMono(testee().findByIdentityId(bob, identity.id)).block())
      .isEqualTo(updateIdentity)
  }

  @Test
  def upsertShouldCreateEntryWhenNotExists(): Unit = {
    val identity: Identity = Identity(id = IDENTITY1.id,
      name = IdentityName("Bob (3)"),
      email = BOB.asMailAddress(),
      replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss (updated)")), new MailAddress("boss-updated@domain.tld")))),
      bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2 (updated)")), new MailAddress("boss-updated-2@domain.tld")))),
      textSignature = TextSignature("text 2 signature"),
      htmlSignature = HtmlSignature("html 2 signature"),
      mayDelete = MayDeleteIdentity(true))

    SMono(testee().upsert(bob, identity)).block()
    assertThat(SMono(testee().findByIdentityId(bob, identity.id)).block())
      .isEqualTo(identity)
  }

  @Test
  def saveShouldDispatchCreatedEvent(): Unit = {
    val collector = new CustomIdentityDAOContract.IdentityEventCollector()
    eventBus.register(collector, collector.getDefaultGroup)

    val identity = SMono(testee().save(bob, CREATION_REQUEST)).block()

    assertThat(collector.events.asScala.map(_.getClass).asJava)
      .containsOnly(classOf[CustomIdentityCreated])
    assertThat(collector.events.asScala.map(_.asInstanceOf[CustomIdentityCreated].identity).asJava)
      .containsOnly(identity)
    assertThat(collector.events.asScala.map(_.getUsername).asJava)
      .containsOnly(bob)
  }

  @Test
  def saveWithIdShouldDispatchCreatedEvent(): Unit = {
    val collector = new CustomIdentityDAOContract.IdentityEventCollector()
    eventBus.register(collector, collector.getDefaultGroup)

    val id = IdentityId.generate
    val identity = SMono(testee().save(bob, id, CREATION_REQUEST)).block()

    assertThat(collector.events.asScala.map(_.getClass).asJava)
      .containsOnly(classOf[CustomIdentityCreated])
    assertThat(collector.events.asScala.map(_.asInstanceOf[CustomIdentityCreated].identity).asJava)
      .containsOnly(identity)
  }

  @Test
  def updateShouldDispatchUpdatedEvent(): Unit = {
    val collector = new CustomIdentityDAOContract.IdentityEventCollector()
    eventBus.register(collector, collector.getDefaultGroup)

    val identity = SMono(testee().save(bob, CREATION_REQUEST)).block()
    collector.events.clear()

    SMono(testee().update(bob, identity.id, IdentityUpdateRequest(
      name = Some(IdentityNameUpdate(IdentityName("Bob (updated)")))))).block()

    assertThat(collector.events.asScala.map(_.getClass).asJava)
      .containsOnly(classOf[CustomIdentityUpdated])
    val updatedEvent = collector.events.get(0).asInstanceOf[CustomIdentityUpdated]
    assertThat(updatedEvent.getUsername).isEqualTo(bob)
    assertThat(updatedEvent.identity.name).isEqualTo(IdentityName("Bob (updated)"))
    assertThat(updatedEvent.identity.id).isEqualTo(identity.id)
  }

  @Test
  def upsertShouldDispatchUpdatedEvent(): Unit = {
    val collector = new CustomIdentityDAOContract.IdentityEventCollector()
    eventBus.register(collector, collector.getDefaultGroup)

    val identity = SMono(testee().save(bob, CREATION_REQUEST)).block()
    collector.events.clear()

    val updatedIdentity = identity.copy(name = IdentityName("Bob (upserted)"))
    SMono(testee().upsert(bob, updatedIdentity)).block()

    assertThat(collector.events.asScala.map(_.getClass).asJava)
      .containsOnly(classOf[CustomIdentityUpdated])
    val updatedEvent = collector.events.get(0).asInstanceOf[CustomIdentityUpdated]
    assertThat(updatedEvent.getUsername).isEqualTo(bob)
    assertThat(updatedEvent.identity.name).isEqualTo(IdentityName("Bob (upserted)"))
  }

  @Test
  def deleteShouldDispatchDeletedEvent(): Unit = {
    val collector = new CustomIdentityDAOContract.IdentityEventCollector()
    eventBus.register(collector, collector.getDefaultGroup)

    val identity = SMono(testee().save(bob, CREATION_REQUEST)).block()
    collector.events.clear()

    SMono(testee().delete(bob, Set(identity.id))).block()

    assertThat(collector.events.asScala.map(_.getClass).asJava)
      .containsOnly(classOf[CustomIdentityDeleted])
    val deletedEvent = collector.events.get(0).asInstanceOf[CustomIdentityDeleted]
    assertThat(deletedEvent.getUsername).isEqualTo(bob)
    assertThat(deletedEvent.identityIds.asJava).containsOnly(identity.id)
  }

  @Test
  def deleteAllShouldDispatchAllDeletedEvent(): Unit = {
    val collector = new CustomIdentityDAOContract.IdentityEventCollector()
    eventBus.register(collector, collector.getDefaultGroup)

    val identity = SMono(testee().save(bob, CREATION_REQUEST)).block()
    collector.events.clear()

    SMono(testee().delete(bob)).block()

    assertThat(collector.events.asScala.map(_.getClass).asJava)
      .containsOnly(classOf[AllCustomIdentitiesDeleted])
    val deletedEvent = collector.events.get(0).asInstanceOf[AllCustomIdentitiesDeleted]
    assertThat(deletedEvent.getUsername).isEqualTo(bob)
    assertThat(deletedEvent.identityIds.asJava).containsOnly(identity.id)
  }
}
