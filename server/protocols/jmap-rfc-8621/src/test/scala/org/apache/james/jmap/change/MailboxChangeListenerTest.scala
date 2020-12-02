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

import java.time.ZonedDateTime
import java.util.UUID

import javax.mail.Flags
import org.apache.james.jmap.api.change.MailboxChange.State
import org.apache.james.jmap.api.change.{MailboxChange, MailboxChangeRepository}
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.MailboxChangeListenerTest.ACCOUNT_ID
import org.apache.james.jmap.memory.change.MemoryMailboxChangeRepository
import org.apache.james.mailbox.MessageManager.{AppendCommand, AppendResult, FlagsUpdateMode}
import org.apache.james.mailbox.events.delivery.InVmEventDelivery
import org.apache.james.mailbox.events.{InVMEventBus, MemoryEventDeadLetters, RetryBackoffConfiguration}
import org.apache.james.mailbox.fixture.MailboxFixture.{ALICE, BOB}
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath, MessageRange, TestId}
import org.apache.james.mailbox.{MailboxManager, MailboxSessionUtil, MessageManager}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object MailboxChangeListenerTest {
  val ACCOUNT_ID = AccountId.fromUsername(BOB)
}

class MailboxChangeListenerTest {

  var repository: MailboxChangeRepository = _
  var mailboxManager: MailboxManager = _
  var listener: MailboxChangeListener = _

  @BeforeEach
  def setUp: Unit = {
    val resources = InMemoryIntegrationResources.builder
      .preProvisionnedFakeAuthenticator
      .fakeAuthorizator
      .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory), RetryBackoffConfiguration.DEFAULT, new MemoryEventDeadLetters))
      .defaultAnnotationLimits.defaultMessageParser.scanningSearchIndex.noPreDeletionHooks.storeQuotaManager
      .build

    mailboxManager = resources.getMailboxManager
    repository = new MemoryMailboxChangeRepository()
    listener = MailboxChangeListener(repository)
    resources.getEventBus.register(listener)
  }

  @Test
  def createMailboxShouldStoreCreatedEvent(): Unit = {
    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    val mailboxSession = MailboxSessionUtil.create(BOB)
    val inboxId: MailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxSession).get

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getCreated)
      .containsExactly(inboxId)
  }

  @Test
  def updateMailboxNameShouldStoreUpdatedEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val newPath = MailboxPath.forUser(BOB, "another")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    mailboxManager.renameMailbox(path, newPath, mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .containsExactly(inboxId)
  }

  @Test
  def updateMailboxACLShouldStoreUpdatedEvent(): Unit = {
    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.inbox(BOB)
    val inboxId: MailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxSession).get

    mailboxManager.applyRightsCommand(path, MailboxACL.command().forUser(ALICE).rights(MailboxACL.Right.Read).asAddition(), mailboxSession);

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .containsExactly(inboxId)
  }

  @Test
  def appendMessageToMailboxShouldStoreUpdateEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    mailboxManager
      .getMailbox(inboxId, mailboxSession)
      .appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .containsExactly(inboxId)
  }

  @Test
  def addSeenFlagsShouldStoreUpdateEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
    val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
    messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    messageManager.setFlags(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .containsExactly(inboxId)
  }

  @Test
  def removeSeenFlagsShouldStoreUpdateEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
    val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
    messageManager.appendMessage(AppendCommand.builder()
      .withFlags(new Flags(Flags.Flag.SEEN))
      .build("header: value\r\n\r\nbody"), mailboxSession)

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    messageManager.setFlags(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REMOVE, MessageRange.all(), mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .containsExactly(inboxId)
  }

  @Test
  def addOtherThanSeenFlagsShouldNotStoreUpdateEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
    val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
    messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    messageManager.setFlags(new Flags(Flags.Flag.ANSWERED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .isEmpty()
  }

  @Test
  def updateOtherThanSeenFlagsShouldNotStoreUpdateEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
    val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
    messageManager.appendMessage(AppendCommand.builder()
      .withFlags(new Flags(Flags.Flag.ANSWERED))
      .build("header: value\r\n\r\nbody"), mailboxSession)

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REPLACE, MessageRange.all(), mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .isEmpty()
  }

  @Test
  def deleteMessageFromMailboxShouldStoreUpdateEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
    val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
    val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()
    messageManager.delete(List(appendResult.getId.getUid).asJava, mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
      .containsExactly(inboxId)
  }

  @Test
  def deleteMailboxNameShouldStoreDestroyedEvent(): Unit = {
    val mailboxSession = MailboxSessionUtil.create(BOB)
    val path = MailboxPath.forUser(BOB, "test")
    val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

    val state = State.of(UUID.randomUUID)
    repository.save(MailboxChange.of(ACCOUNT_ID, state, ZonedDateTime.now, List[MailboxId](TestId.of(0)).asJava, List().asJava, List().asJava)).block()

    mailboxManager.deleteMailbox(inboxId, mailboxSession)

    assertThat(repository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getDestroyed)
      .containsExactly(inboxId)
  }
}
