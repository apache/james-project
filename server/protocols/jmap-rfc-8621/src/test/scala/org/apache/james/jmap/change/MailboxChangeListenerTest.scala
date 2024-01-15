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


import java.time.{Clock, ZonedDateTime}

import com.google.common.collect.ImmutableList
import javax.mail.Flags
import org.apache.james.events.delivery.InVmEventDelivery
import org.apache.james.events.{Event, EventBus, InVMEventBus, MemoryEventDeadLetters, RegistrationKey, RetryBackoffConfiguration}
import org.apache.james.jmap.api.change.{EmailChange, EmailChangeRepository, Limit, MailboxAndEmailChange, MailboxChange, MailboxChangeRepository, State}
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.MailboxChangeListenerTest.{ACCOUNT_ID, DEFAULT_NUMBER_OF_CHANGES}
import org.apache.james.jmap.memory.change.{MemoryEmailChangeRepository, MemoryMailboxChangeRepository}
import org.apache.james.mailbox.MessageManager.{AppendCommand, AppendResult, FlagsUpdateMode}
import org.apache.james.mailbox.fixture.MailboxFixture.{ALICE, BOB}
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath, MessageRange, TestId, TestMessageId}
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.apache.james.mailbox.{MailboxManager, MailboxSessionUtil, MessageManager, SubscriptionManager}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Nested, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object MailboxChangeListenerTest {
  val ACCOUNT_ID = AccountId.fromUsername(BOB)
  val DEFAULT_NUMBER_OF_CHANGES: Limit = Limit.of(5)
}

class MailboxChangeListenerTest {

  var mailboxChangeRepository: MailboxChangeRepository = _
  var mailboxManager: MailboxManager = _
  var mailboxChangeFactory: MailboxChange.Factory = _
  var emailChangeRepository: EmailChangeRepository = _
  var emailChangeFactory: MailboxAndEmailChange.Factory = _
  var stateFactory: State.Factory = _
  var listener: MailboxChangeListener = _
  var clock: Clock = _
  var subscriptionManager: SubscriptionManager = _

  @BeforeEach
  def setUp: Unit = {
    val resources = InMemoryIntegrationResources.builder
      .preProvisionnedFakeAuthenticator
      .fakeAuthorizator
      .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory), RetryBackoffConfiguration.DEFAULT, new MemoryEventDeadLetters))
      .defaultAnnotationLimits.defaultMessageParser.scanningSearchIndex.noPreDeletionHooks.storeQuotaManager
      .build

    clock = Clock.systemUTC()
    mailboxManager = resources.getMailboxManager
    stateFactory = new State.DefaultFactory
    mailboxChangeFactory = new MailboxChange.Factory(stateFactory)
    mailboxChangeRepository = new MemoryMailboxChangeRepository(DEFAULT_NUMBER_OF_CHANGES)
    emailChangeFactory = new MailboxAndEmailChange.Factory(stateFactory, resources.getMessageIdManager, resources.getMailboxManager)
    emailChangeRepository = new MemoryEmailChangeRepository(DEFAULT_NUMBER_OF_CHANGES)
    val eventBus = Mockito.mock(classOf[EventBus])
    Mockito.when(eventBus.dispatch(any(classOf[Event]), any(classOf[RegistrationKey])))
      .thenReturn(Mono.empty())

    subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager.getMapperFactory,
      resources.getMailboxManager.getMapperFactory,
      resources.getEventBus)
    listener = MailboxChangeListener(eventBus, mailboxChangeRepository, mailboxChangeFactory, emailChangeRepository, emailChangeFactory, mailboxManager, clock)
    resources.getEventBus.register(listener)
  }

  @Nested
  class MailboxChangeEvents {
    @Test
    def createMailboxShouldStoreCreatedEvent(): Unit = {
      val state = stateFactory.generate()
      mailboxChangeRepository.save(MailboxChange.builder().accountId(ACCOUNT_ID).state(state).date(ZonedDateTime.now).isCountChange(false).created(List[MailboxId](TestId.of(0)).asJava).build).block()

      val mailboxSession = MailboxSessionUtil.create(BOB)
      val inboxId: MailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxSession).get

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getCreated)
        .containsExactly(inboxId)
    }

    @Test
    def updateMailboxNameShouldStoreUpdatedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val newPath = MailboxPath.forUser(BOB, "another")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

      val state = stateFactory.generate()
      mailboxChangeRepository.save(MailboxChange.builder().accountId(ACCOUNT_ID).state(state).date(ZonedDateTime.now).isCountChange(false).created(List[MailboxId](TestId.of(0)).asJava).build).block()

      mailboxManager.renameMailbox(path, newPath, mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }

    @Test
    def updateMailboxACLShouldStoreUpdatedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.inbox(BOB)
      val inboxId: MailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxSession).get
      val state: State = mailboxChangeRepository.getLatestState(ACCOUNT_ID).block()

      mailboxManager.applyRightsCommand(path, MailboxACL.command().forUser(ALICE).rights(MailboxACL.Right.Read).asAddition(), mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }

    @Test
    def appendMessageToMailboxShouldStoreUpdateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

      val state = stateFactory.generate()
      mailboxChangeRepository.save(MailboxChange.builder().accountId(ACCOUNT_ID).state(state).date(ZonedDateTime.now).isCountChange(false).created(List[MailboxId](TestId.of(0)).asJava).build).block()

      mailboxManager.applyRightsCommand(path, MailboxACL.command().forUser(ALICE).rights(MailboxACL.Right.Read).asAddition(), mailboxSession)

      mailboxManager
        .getMailbox(inboxId, mailboxSession)
        .appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }

    @Test
    def addSeenFlagsShouldStoreUpdateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = stateFactory.generate()
      mailboxChangeRepository.save(MailboxChange.builder().accountId(ACCOUNT_ID).state(state).date(ZonedDateTime.now).isCountChange(false).created(List[MailboxId](TestId.of(0)).asJava).build).block()

      messageManager.setFlags(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }

    @Test
    def addDeleteFlagsShouldStoreUpdatedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getUpdated).containsExactly(inboxId)
      })
    }

    private def dummyState(): State = {
      val state = stateFactory.generate()
      mailboxChangeRepository.save(MailboxChange.builder().accountId(ACCOUNT_ID).state(state).date(ZonedDateTime.now).isCountChange(false).created(List[MailboxId](TestId.of(0)).asJava).build).block()
      state
    }

    @Test
    def expungeShouldNotAlterState(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)


      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      val state = dummyState()

      messageManager.expunge(MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def removeDeleteFlagsShouldStoreUpdatedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)


      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REMOVE, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getUpdated).containsExactly(inboxId)
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def updatingAnAlreadyDeletedMessageShouldNoop(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def shouldUpdateWhenCopy(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path1 = MailboxPath.forUser(BOB, "test")
      val mailboxId1: MailboxId = mailboxManager.createMailbox(path1, mailboxSession).get
      val messageManager1: MessageManager = mailboxManager.getMailbox(mailboxId1, mailboxSession)
      val appendResult1: AppendResult = messageManager1.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val path2 = MailboxPath.forUser(BOB, "test2")
      val mailboxId2: MailboxId = mailboxManager.createMailbox(path2, mailboxSession).get
      val messageManager2: MessageManager = mailboxManager.getMailbox(mailboxId2, mailboxSession)

      val state = dummyState()

      mailboxManager.copyMessages(MessageRange.one(appendResult1.getId.getUid), mailboxId1, mailboxId2, mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getUpdated).containsExactly(mailboxId2)
      })
    }

    @Test
    def shouldUpdateWhenMove(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path1 = MailboxPath.forUser(BOB, "test")
      val mailboxId1: MailboxId = mailboxManager.createMailbox(path1, mailboxSession).get
      val messageManager1: MessageManager = mailboxManager.getMailbox(mailboxId1, mailboxSession)
      val appendResult1: AppendResult = messageManager1.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val path2 = MailboxPath.forUser(BOB, "test2")
      val mailboxId2: MailboxId = mailboxManager.createMailbox(path2, mailboxSession).get
      val messageManager2: MessageManager = mailboxManager.getMailbox(mailboxId2, mailboxSession)

      val state = dummyState()

      mailboxManager.moveMessages(MessageRange.one(appendResult1.getId.getUid), mailboxId1, mailboxId2, mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getUpdated).containsOnly(mailboxId1, mailboxId2)
      })
    }

    @Test
    def setDeleteFlagShouldUpdateWhenWithSeenFlag(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      val flags = new Flags(Flags.Flag.SEEN)
      flags.add(Flags.Flag.DELETED)
      messageManager.setFlags(flags, FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).containsExactly(inboxId)
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
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

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REMOVE, MessageRange.all(), mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }

    @Test
    def addOtherThanSeenFlagsShouldNotStoreUpdateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.ANSWERED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
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

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DRAFT), FlagsUpdateMode.REPLACE, MessageRange.all(), mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .isEmpty()
    }

    @Test
    def deleteMessageFromMailboxShouldStoreUpdateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.delete(List(appendResult.getId.getUid).asJava, mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }

    @Test
    def deleteMailboxNameShouldStoreDestroyedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

      val state = dummyState()

      mailboxManager.deleteMailbox(inboxId, mailboxSession)

      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getDestroyed)
        .containsExactly(inboxId)
    }

    @Test
    def subscribeMailboxShouldStoreMailboxSubscribedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

      val state = dummyState()

      subscriptionManager.subscribe(mailboxSession, path)
      Thread.sleep(200)
      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }

    @Test
    def unSubscribeMailboxShouldStoreMailboxUnSubscribedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

      val state = dummyState()

      subscriptionManager.unsubscribe(mailboxSession, path)
      Thread.sleep(200)
      assertThat(mailboxChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block().getUpdated)
        .containsExactly(inboxId)
    }
  }

  @Nested
  class EmailChangeEvents {
    @Test
    def appendMessageToMailboxShouldStoreCreatedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get

      val state = dummyState()

      val appendResult: AppendResult = mailboxManager
        .getMailbox(inboxId, mailboxSession)
        .appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getCreated).containsExactly(appendResult.getId.getMessageId)
      })
    }

    @Test
    def addFlagsShouldStoreUpdateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.ANSWERED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getUpdated).containsExactly(appendResult.getId.getMessageId)
      })
    }

    @Test
    def removeSeenFlagsShouldStoreUpdateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder()
        .withFlags(new Flags(Flags.Flag.DRAFT))
        .build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DRAFT), FlagsUpdateMode.REMOVE, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getUpdated).containsExactly(appendResult.getId.getMessageId)
      })
    }

    @Test
    def deleteMessageFromMailboxShouldStoreUpdateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.delete(List(appendResult.getId.getUid).asJava, mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).containsExactly(appendResult.getId.getMessageId)
      })
    }

    @Test
    def addDeleteFlagsShouldStoreDestroyedEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).containsExactly(appendResult.getId.getMessageId)
      })
    }

    @Test
    def addDeleteFlagsShouldStoreDestroyedEventWhenMixedCase(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult1: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val appendResult2: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.one(appendResult1.getId.getUid), mailboxSession)
      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).containsExactly(appendResult2.getId.getMessageId)
      })
    }

    @Test
    def shouldUpdateWhenCopy(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path1 = MailboxPath.forUser(BOB, "test")
      val mailboxId1: MailboxId = mailboxManager.createMailbox(path1, mailboxSession).get
      val messageManager1: MessageManager = mailboxManager.getMailbox(mailboxId1, mailboxSession)
      val appendResult1: AppendResult = messageManager1.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val path2 = MailboxPath.forUser(BOB, "test2")
      val mailboxId2: MailboxId = mailboxManager.createMailbox(path2, mailboxSession).get
      val messageManager2: MessageManager = mailboxManager.getMailbox(mailboxId2, mailboxSession)

      val state = dummyState()

      mailboxManager.copyMessages(MessageRange.one(appendResult1.getId.getUid), mailboxId1, mailboxId2, mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getUpdated).containsExactly(appendResult1.getId.getMessageId)
      })
    }

    @Test
    def shouldUpdateWhenMove(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path1 = MailboxPath.forUser(BOB, "test")
      val mailboxId1: MailboxId = mailboxManager.createMailbox(path1, mailboxSession).get
      val messageManager1: MessageManager = mailboxManager.getMailbox(mailboxId1, mailboxSession)
      val appendResult1: AppendResult = messageManager1.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val path2 = MailboxPath.forUser(BOB, "test2")
      val mailboxId2: MailboxId = mailboxManager.createMailbox(path2, mailboxSession).get
      val messageManager2: MessageManager = mailboxManager.getMailbox(mailboxId2, mailboxSession)

      val state = dummyState()

      mailboxManager.moveMessages(MessageRange.one(appendResult1.getId.getUid), mailboxId1, mailboxId2, mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
        softly.assertThat(changes.getUpdated).containsExactly(appendResult1.getId.getMessageId)
      })
    }

    @Test
    def expungeShouldNotAlterState(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      val state = dummyState()

      messageManager.expunge(MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def expungeShouldHandleMixedCase(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult1: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val appendResult2: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.one(appendResult1.getId.getUid), mailboxSession)

      val state = dummyState()

      messageManager.delete(ImmutableList.of(appendResult1.getId.getUid, appendResult2.getId.getUid), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).containsExactly(appendResult2.getId.getMessageId)
      })
    }

    @Test
    def setFlagsShouldNotTriggerChangeWhenNoop(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult1: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val appendResult2: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.ADD, MessageRange.one(appendResult1.getId.getUid), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.ADD, MessageRange.one(appendResult1.getId.getUid), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }
    @Test
    def setFlagsShouldIgnoreRecent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().notRecent().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.RECENT), FlagsUpdateMode.ADD, MessageRange.one(appendResult.getId.getUid), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def removeDeleteFlagsShouldStoreCreateEvent(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REMOVE, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).containsExactly(appendResult.getId.getMessageId)
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def removeDeleteFlagsShouldStoreCreateEventWhenMixedCase(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult1: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)
      val appendResult2: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.one(appendResult1.getId.getUid), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REMOVE, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).containsExactly(appendResult1.getId.getMessageId)
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def updatingAnAlreadyDeletedMessageShouldNoop(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      val state = dummyState()

      messageManager.setFlags(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).isEmpty()
      })
    }

    @Test
    def setDeleteFlagShouldDestroyWhenWithOtherFlags(): Unit = {
      val mailboxSession = MailboxSessionUtil.create(BOB)
      val path = MailboxPath.forUser(BOB, "test")
      val inboxId: MailboxId = mailboxManager.createMailbox(path, mailboxSession).get
      val messageManager: MessageManager = mailboxManager.getMailbox(inboxId, mailboxSession)
      val appendResult: AppendResult = messageManager.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession)

      val state = dummyState()

      val flags = new Flags(Flags.Flag.FLAGGED)
      flags.add(Flags.Flag.DELETED)
      messageManager.setFlags(flags, FlagsUpdateMode.ADD, MessageRange.all(), mailboxSession)

      SoftAssertions.assertSoftly(softly => {
        val changes = emailChangeRepository.getSinceState(ACCOUNT_ID, state, None.toJava).block()
        softly.assertThat(changes.getCreated).isEmpty()
        softly.assertThat(changes.getUpdated).isEmpty()
        softly.assertThat(changes.getDestroyed).containsExactly(appendResult.getId.getMessageId)
      })
    }

    private def dummyState(): State = {
      val state = stateFactory.generate()
      emailChangeRepository.save(EmailChange.builder()
        .accountId(ACCOUNT_ID)
        .state(state)
        .date(ZonedDateTime.now)
        .isShared(false)
        .created(TestMessageId.of(0))
        .build)
        .block()
      state
    }
  }
}
