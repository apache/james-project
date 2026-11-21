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

package org.apache.james.mailbox.inmemory;

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Allocating a mod-sequence is costly (a lightweight transaction on Cassandra): removing messages from a mailbox
 * should allocate a single one per operation, whatever the number of batches or ranges.
 */
class ModSeqAllocationOnRemovalTest {
    private static final Username USER = Username.of("user");
    private static final int MESSAGE_COUNT = 3;

    private InMemoryMailboxManager mailboxManager;
    private MailboxSession session;
    private MessageManager inbox;
    private MailboxId otherId;
    private List<MessageUid> uids;

    private EventBus eventBus;

    @BeforeEach
    void setUp() throws Exception {
        eventBus = spy(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()),
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters()));
        mailboxManager = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(eventBus)
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .batchSizes(BatchSizes.builder().copyBatchSize(1).build())
            .build()
            .getMailboxManager();
        session = mailboxManager.createSystemSession(USER);
        MailboxId inboxId = mailboxManager.createMailbox(MailboxPath.inbox(session), session).get();
        otherId = mailboxManager.createMailbox(MailboxPath.forUser(USER, "other"), session).get();
        inbox = mailboxManager.getMailbox(inboxId, session);

        ImmutableList.Builder<MessageUid> appended = ImmutableList.builder();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            appended.add(inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                    .setSubject("message " + i)
                    .setBody("body", StandardCharsets.UTF_8)), session)
                .getId().getUid());
        }
        uids = appended.build();
    }

    @Test
    void moveShouldAllocateASingleModSeqForAllItsBatches() throws Exception {
        ModSeq before = highestModSeq();

        mailboxManager.moveMessages(MessageRange.all(), inbox.getId(), otherId, session);

        assertThat(highestModSeq()).isEqualTo(before.next());
    }

    @Test
    void moveOfSeveralRangesShouldAllocateASingleModSeq() throws Exception {
        ModSeq before = highestModSeq();

        Flux.from(mailboxManager.moveMessagesReactive(singleUidRanges(), inbox.getId(), otherId, session))
            .collectList()
            .block();

        assertThat(highestModSeq()).isEqualTo(before.next());
    }

    @Test
    void expungeOfSeveralRangesShouldAllocateASingleModSeq() throws Exception {
        inbox.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);
        ModSeq before = highestModSeq();

        List<MessageUid> expunged = inbox.expungeRangesReactive(singleUidRanges(), session).collectList().block();

        assertThat(expunged).containsExactlyInAnyOrderElementsOf(uids);
        assertThat(highestModSeq()).isEqualTo(before.next());
    }

    @Test
    void moveOfSeveralRangesShouldStillRaiseTheModSeqWhenItFailsAfterRemovingMessages() throws Exception {
        ModSeq before = highestModSeq();
        // The first batch is moved, then dispatching its events fails
        doReturn(Mono.error(new RuntimeException("dispatch failure"))).when(eventBus).dispatch(anyCollection());

        assertThatThrownBy(() -> Flux.from(mailboxManager.moveMessagesReactive(singleUidRanges(), inbox.getId(), otherId, session))
                .collectList()
                .block())
            .isInstanceOf(RuntimeException.class);

        assertThat(inbox.getMessageCount(session)).isLessThan(MESSAGE_COUNT);
        assertThat(highestModSeq()).isGreaterThan(before);
    }

    @Test
    void moveOfASingleRangeShouldStillRaiseTheModSeqWhenItFailsAfterRemovingMessages() throws Exception {
        ModSeq before = highestModSeq();
        doReturn(Mono.error(new RuntimeException("dispatch failure"))).when(eventBus).dispatch(anyCollection());

        assertThatThrownBy(() -> Flux.from(mailboxManager.moveMessagesReactive(MessageRange.all(), inbox.getId(), otherId, session))
                .collectList()
                .block())
            .isInstanceOf(RuntimeException.class);

        assertThat(inbox.getMessageCount(session)).isLessThan(MESSAGE_COUNT);
        assertThat(highestModSeq()).isGreaterThan(before);
    }

    @Test
    void expungeOfNothingShouldNotAllocateAModSeq() throws Exception {
        ModSeq before = highestModSeq();

        List<MessageUid> expunged = inbox.expungeRangesReactive(singleUidRanges(), session).collectList().block();

        assertThat(expunged).isEmpty();
        assertThat(highestModSeq()).isEqualTo(before);
    }

    private List<MessageRange> singleUidRanges() {
        return uids.stream()
            .map(MessageRange::one)
            .collect(ImmutableList.toImmutableList());
    }

    private ModSeq highestModSeq() throws Exception {
        return inbox.getMetaData(IGNORE, session, MessageManager.MailboxMetaData.FetchGroup.NO_UNSEEN)
            .getHighestModSeq();
    }
}
