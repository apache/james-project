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

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.fixture.MailboxFixture.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxEvent;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public abstract class AbstractMessageIdManagerSideEffectTest {
    private static final Quota<QuotaCountLimit, QuotaCountUsage> OVER_QUOTA = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
        .used(QuotaCountUsage.count(102))
        .computedLimit(QuotaCountLimit.count(100))
        .build();
    protected static final MessageUid messageUid1 = MessageUid.of(111);
    protected static final MessageUid messageUid2 = MessageUid.of(113);

    protected static final Flags FLAGS = new Flags();

    protected MessageIdManager messageIdManager;
    protected MailboxSession session;
    protected Mailbox mailbox1;
    protected Mailbox mailbox2;
    protected Mailbox mailbox3;
    private QuotaManager quotaManager;
    protected MessageIdManagerTestSystem testingData;
    private EventCollector eventCollector;
    private EventBus eventBus;
    private PreDeletionHook preDeletionHook1;
    private PreDeletionHook preDeletionHook2;

    protected abstract MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, EventBus eventBus, Set<PreDeletionHook> preDeletionHooks) throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), StoreMailboxManagerTest.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        eventCollector = new EventCollector();
        quotaManager = mock(QuotaManager.class);

        when(quotaManager.getQuotasReactive(any(QuotaRoot.class)))
            .thenReturn(Mono.just(new QuotaManager.Quotas(
                Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(102)).computedLimit(QuotaCountLimit.unlimited()).build(),
                Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(2)).computedLimit(QuotaSizeLimit.unlimited()).build())));

        session = MailboxSessionUtil.create(ALICE);
        setupMockForPreDeletionHooks();
        testingData = createTestSystem(quotaManager, eventBus, ImmutableSet.of(preDeletionHook1, preDeletionHook2));
        messageIdManager = testingData.getMessageIdManager();

        mailbox1 = testingData.createMailbox(MailboxFixture.INBOX_ALICE, session);
        mailbox2 = testingData.createMailbox(MailboxFixture.OUTBOX_ALICE, session);
        mailbox3 = testingData.createMailbox(MailboxFixture.SENT_ALICE, session);
    }

    private void setupMockForPreDeletionHooks() {
        preDeletionHook1 = mock(PreDeletionHook.class);
        when(preDeletionHook1.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
            .thenReturn(Mono.empty());

        preDeletionHook2 = mock(PreDeletionHook.class);
        when(preDeletionHook2.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
            .thenReturn(Mono.empty());
    }

    @Test
    void deleteShouldCallEventDispatcher() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);

        MessageResult messageResult = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session).get(0);
        MessageMetaData simpleMessageMetaData = messageResult.messageMetaData();

        eventBus.register(eventCollector);
        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(eventCollector.getEvents())
            .filteredOn(event -> event instanceof Expunged)
            .hasSize(1).first()
            .satisfies(e -> {
                Expunged event = (Expunged) e;
                assertThat(event.getMailboxId()).isEqualTo(mailbox1.getMailboxId());
                assertThat(event.getMailboxPath()).isEqualTo(mailbox1.generateAssociatedPath());
                assertThat(event.getExpunged().values()).containsOnly(simpleMessageMetaData);
            });
    }

    @SuppressWarnings("unchecked")
    @Test
    void deletesShouldCallEventDispatcher() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        MessageId messageId2 = testingData.persist(mailbox1.getMailboxId(), messageUid2, FLAGS, session);

        MessageResult messageResult1 = messageIdManager.getMessage(messageId1, FetchGroup.MINIMAL, session).get(0);
        MessageMetaData simpleMessageMetaData1 = messageResult1.messageMetaData();
        MessageResult messageResult2 = messageIdManager.getMessage(messageId2, FetchGroup.MINIMAL, session).get(0);
        MessageMetaData simpleMessageMetaData2 = messageResult2.messageMetaData();

        eventBus.register(eventCollector);
        Mono.from(messageIdManager.delete(ImmutableList.of(messageId1, messageId2), session))
            .subscribeOn(Schedulers.newSingle("test"))
            .block();

        AbstractListAssert<?, List<? extends Expunged>, Expunged, ObjectAssert<Expunged>> events =
            assertThat(eventCollector.getEvents())
                .filteredOn(event -> event instanceof Expunged)
                .hasSize(2)
                .extracting(event -> (Expunged) event);
        events.extracting(MailboxEvent::getMailboxId).containsOnly(mailbox1.getMailboxId(), mailbox1.getMailboxId());
        events.extracting(Expunged::getExpunged)
            .containsOnly(ImmutableSortedMap.of(simpleMessageMetaData1.getUid(), simpleMessageMetaData1),
                ImmutableSortedMap.of(simpleMessageMetaData2.getUid(), simpleMessageMetaData2));
    }

    @Test
    void deleteShouldNotCallEventDispatcherWhenMessageIsInWrongMailbox() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        eventBus.register(eventCollector);
        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void deletesShouldCallAllPreDeletionHooks() throws Exception {
        givenUnlimitedQuota();

        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
        ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
        verify(preDeletionHook1, times(1)).notifyDelete(preDeleteCaptor1.capture());
        verify(preDeletionHook2, times(1)).notifyDelete(preDeleteCaptor2.capture());

        assertThat(preDeleteCaptor1.getValue().getDeletionMetadataList())
            .hasSize(1)
            .hasSameElementsAs(preDeleteCaptor2.getValue().getDeletionMetadataList())
            .allSatisfy(deleteMetadata -> SoftAssertions.assertSoftly(softy -> {
                softy.assertThat(deleteMetadata.getMailboxId()).isEqualTo(mailbox1.getMailboxId());
                softy.assertThat(deleteMetadata.getMessageId()).isEqualTo(messageId);
            }));

    }

    @Test
    void deletesShouldCallAllPreDeletionHooksOnEachMessageDeletionCall() throws Exception {
        givenUnlimitedQuota();

        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        MessageId messageId2 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.delete(messageId1, ImmutableList.of(mailbox1.getMailboxId()), session);
        messageIdManager.delete(messageId2, ImmutableList.of(mailbox1.getMailboxId()), session);

        ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
        ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
        verify(preDeletionHook1, times(2)).notifyDelete(preDeleteCaptor1.capture());
        verify(preDeletionHook2, times(2)).notifyDelete(preDeleteCaptor2.capture());

        assertThat(preDeleteCaptor1.getAllValues())
            .hasSize(2)
            .hasSameElementsAs(preDeleteCaptor2.getAllValues())
            .flatExtracting(PreDeletionHook.DeleteOperation::getDeletionMetadataList)
            .allSatisfy(deleteMetadata -> SoftAssertions.assertSoftly(softy -> {
                softy.assertThat(deleteMetadata.getMailboxId()).isEqualTo(mailbox1.getMailboxId());
            }))
            .extracting(deleteMetadata -> deleteMetadata.getMessageId())
            .containsOnly(messageId1, messageId2);

    }

    @Test
    void deletesShouldCallAllPreDeletionHooksOnEachMessageDeletionOnDifferentMailboxes() throws Exception {
        givenUnlimitedQuota();

        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        MessageId messageId2 = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.delete(messageId1, ImmutableList.of(mailbox1.getMailboxId()), session);
        messageIdManager.delete(messageId2, ImmutableList.of(mailbox2.getMailboxId()), session);

        ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
        ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
        verify(preDeletionHook1, times(2)).notifyDelete(preDeleteCaptor1.capture());
        verify(preDeletionHook2, times(2)).notifyDelete(preDeleteCaptor2.capture());

        assertThat(preDeleteCaptor1.getAllValues())
            .hasSameElementsAs(preDeleteCaptor2.getAllValues())
            .flatExtracting(PreDeletionHook.DeleteOperation::getDeletionMetadataList)
            .extracting(deleteMetadata -> deleteMetadata.getMessageId())
            .containsOnly(messageId1, messageId2);

        assertThat(preDeleteCaptor1.getAllValues())
            .hasSameElementsAs(preDeleteCaptor2.getAllValues())
            .flatExtracting(PreDeletionHook.DeleteOperation::getDeletionMetadataList)
            .extracting(MetadataWithMailboxId::getMailboxId)
            .containsOnly(mailbox1.getMailboxId(), mailbox2.getMailboxId());
    }

    @Test
    void deletesShouldNotBeExecutedWhenOneOfPreDeleteHooksFails() throws Exception {
        givenUnlimitedQuota();
        when(preDeletionHook1.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
            .thenThrow(new RuntimeException("throw at hook 1"));

        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        assertThatThrownBy(() -> messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session))
            .isInstanceOf(RuntimeException.class);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session)
                .stream()
                .map(MessageResult::getMessageId))
            .hasSize(1)
            .containsOnly(messageId);
    }

    @Test
    void deletesShouldBeExecutedAfterAllHooksFinish() throws Exception {
        givenUnlimitedQuota();

        CountDownLatch latchForHook1 = new CountDownLatch(1);
        when(preDeletionHook1.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
            .thenAnswer(invocation -> {
                latchForHook1.countDown();
                return Mono.empty();
            });

        CountDownLatch latchForHook2 = new CountDownLatch(1);
        when(preDeletionHook2.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
            .thenAnswer(invocation -> {
                latchForHook2.countDown();
                return Mono.empty();
            });

        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        latchForHook1.await();
        latchForHook2.await();

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session))
            .isEmpty();
    }

    @Test
    void setInMailboxesShouldNotCallDispatcherWhenMessageAlreadyInMailbox() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);

        eventBus.register(eventCollector);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void setInMailboxesShouldCallDispatcher() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        eventBus.register(eventCollector);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof MessageMoveEvent).hasSize(1);
        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof Added).hasSize(1)
            .extracting(event -> (Added) event).extracting(Added::getMailboxId)
            .containsOnly(mailbox1.getMailboxId());
    }

    @Test
    void setInMailboxesShouldUseTheRightMessageUidsInExpunged() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);
        testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        testingData.persist(mailbox1.getMailboxId(), messageUid2, FLAGS, session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()), session);
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session);
        MessageUid uidMailbox1 = messages.stream().filter(message -> message.getMailboxId().equals(mailbox1.getMailboxId())).map(MessageResult::getUid).findFirst().get();
        MessageUid uidMailbox3 = messages.stream().filter(message -> message.getMailboxId().equals(mailbox3.getMailboxId())).map(MessageResult::getUid).findFirst().get();


        eventBus.register(eventCollector);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof MessageMoveEvent).hasSize(1);
        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof Expunged).hasSize(2)
            .extracting(event -> (Expunged) event)
            .extracting(event -> Pair.of(event.getMailboxId(), ImmutableList.copyOf(event.getUids())))
            .containsOnly(Pair.of(mailbox1.getMailboxId(), ImmutableList.of(uidMailbox1)), Pair.of(mailbox3.getMailboxId(), ImmutableList.of(uidMailbox3)));
    }

    @Test
    void setInMailboxesShouldCallDispatcherWithMultipleMailboxes() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        eventBus.register(eventCollector);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()), session);

        messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session);

        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof MessageMoveEvent).hasSize(1);
        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof Added).hasSize(2)
            .extracting(event -> (Added) event).extracting(Added::getMailboxId)
            .containsOnly(mailbox1.getMailboxId(), mailbox3.getMailboxId());
    }

    @Test
    void setInMailboxesShouldThrowExceptionWhenOverQuota() {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);

        when(quotaManager.getQuotasReactive(any(QuotaRoot.class)))
            .thenReturn(Mono.just(new QuotaManager.Quotas(
                OVER_QUOTA,
                Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(2)).computedLimit(QuotaSizeLimit.unlimited()).build())));

        assertThatThrownBy(() -> messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()),
                session))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void setInMailboxesShouldCallDispatchForOnlyAddedAndRemovedMailboxes() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        List<MessageResult> messageResults = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session);
        assertThat(messageResults).hasSize(2);

        eventBus.register(eventCollector);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox3.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof MessageMoveEvent).hasSize(1);
        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof Added).hasSize(1)
            .extracting(event -> (Added) event).extracting(Added::getMailboxId)
            .containsOnly(mailbox3.getMailboxId());
        assertThat(eventCollector.getEvents()).filteredOn(event -> event instanceof Expunged).hasSize(1)
            .extracting(event -> (Expunged) event).extracting(Expunged::getMailboxId)
            .containsOnly(mailbox2.getMailboxId());
    }

    @Test
    void setFlagsShouldNotDispatchWhenFlagAlreadySet() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, newFlags, session);

        eventBus.register(eventCollector);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void setFlagsShouldNotDispatchWhenMessageAlreadyInMailbox() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, newFlags, session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        eventBus.register(eventCollector);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void setFlagsShouldNotDispatchWhenMessageDoesNotBelongToMailbox() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);

        eventBus.register(eventCollector);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void setFlagsShouldNotDispatchWhenEmptyMailboxes() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);

        eventBus.register(eventCollector);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void setFlagsShouldDispatchWhenMessageBelongsToAllMailboxes() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        eventBus.register(eventCollector);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).hasSize(2).allSatisfy(event -> assertThat(event).isInstanceOf(FlagsUpdated.class));
    }

    @Test
    void setFlagsShouldDispatchWhenMessageBelongsToTheMailboxes() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        eventBus.register(eventCollector);
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session);
        assertThat(messages).hasSize(1);
        MessageResult messageResult = messages.get(0);
        MessageUid messageUid = messageResult.getUid();
        ModSeq modSeq = messageResult.getModSeq();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(modSeq)
            .oldFlags(FLAGS)
            .newFlags(newFlags)
            .build();

        assertThat(eventCollector.getEvents()).hasSize(1).first().isInstanceOf(FlagsUpdated.class)
            .satisfies(e -> {
                FlagsUpdated event = (FlagsUpdated) e;
                assertThat(event.getUpdatedFlags()).containsOnly(updatedFlags);
                assertThat(event.getMailboxId()).isEqualTo(mailbox2.getMailboxId());
            });
    }

    @Test
    void deleteShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();

        eventBus.register(eventCollector);
        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void deletesShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();

        eventBus.register(eventCollector);
        messageIdManager.delete(messageId, session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void setFlagsShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();

        eventBus.register(eventCollector);
        messageIdManager.setFlags(FLAGS, FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Test
    void setInMailboxesShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();

        eventBus.register(eventCollector);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(eventCollector.getEvents()).isEmpty();
    }

    protected void givenUnlimitedQuota() {
        when(quotaManager.getQuotasReactive(any(QuotaRoot.class)))
            .thenReturn(Mono.just(new QuotaManager.Quotas(
                Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(2)).computedLimit(QuotaCountLimit.unlimited()).build(),
                Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(2)).computedLimit(QuotaSizeLimit.unlimited()).build())));
    }
}
