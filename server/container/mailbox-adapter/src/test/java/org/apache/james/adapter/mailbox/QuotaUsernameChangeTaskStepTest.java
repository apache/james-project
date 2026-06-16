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

package org.apache.james.adapter.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Durations.TEN_SECONDS;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.quota.task.RecomputeMailboxCurrentQuotasService;
import org.apache.james.mime4j.dom.Message;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class QuotaUsernameChangeTaskStepTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");
    // Quotas resulting from a single appended message, see appendAMessage.
    private static final CurrentQuotas ONE_MESSAGE_QUOTAS = new CurrentQuotas(QuotaCountUsage.count(1L), QuotaSizeUsage.size(103L));

    private QuotaUsernameChangeTaskStep testee;
    private InMemoryMailboxManager mailboxManager;
    private QuotaManager quotaManager;
    private CurrentQuotaManager currentQuotaManager;
    private UserQuotaRootResolver quotaRootResolver;
    private MaxQuotaManager maxQuotaManager;

    private ArrayList<Event> eventStore;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        quotaManager = resources.getQuotaManager();
        currentQuotaManager = resources.getCurrentQuotaManager();
        quotaRootResolver = resources.getDefaultUserQuotaRootResolver();
        maxQuotaManager = resources.getMaxQuotaManager();

        eventStore  = new ArrayList<>();

        EventBus eventBus = resources.getEventBus();

        eventBus.register(new EventListener.GroupEventListener() {
            @Override
            public Group getDefaultGroup() {
                return new GenericGroup("test");
            }

            @Override
            public void event(Event event) {
                eventStore.add(event);
            }
        });

        RecomputeMailboxCurrentQuotasService recomputeMailboxCurrentQuotasService = new RecomputeMailboxCurrentQuotasService(
            currentQuotaManager,
            resources.getCurrentQuotaCalculator(),
            quotaRootResolver,
            mailboxManager.getSessionProvider(),
            mailboxManager,
            quotaManager,
            eventBus);

        testee = new QuotaUsernameChangeTaskStep(
            quotaManager,
            recomputeMailboxCurrentQuotasService,
            quotaRootResolver,
            maxQuotaManager);
    }

    @Test
    void shouldMigrateMaxQuotas() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(50));
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(100));

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));

        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota().getLimit()).isEqualTo(QuotaCountLimit.count(50));
            softly.assertThat(aliceQuotas.getStorageQuota().getLimit()).isEqualTo(QuotaSizeLimit.size(100));
        });
    }

    @Test
    void shouldRecomputeCurrentQuotasFromNewUserMailboxes() throws Exception {
        appendAMessage(ALICE);

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(quotaRootResolver.forUser(ALICE))).block())
            .isEqualTo(ONE_MESSAGE_QUOTAS);
    }

    @Test
    void shouldNotOverwriteDestinationCurrentQuotasWithSourceOnes() throws Exception {
        // Source (BOB) holds a current quota value...
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(50));
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(100));

        // ...while the destination (ALICE) already has its own content.
        appendAMessage(ALICE);

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        // ALICE current quotas reflect her actual mailboxes content, not BOB's figures.
        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(quotaRootResolver.forUser(ALICE))).block())
            .isEqualTo(ONE_MESSAGE_QUOTAS);
    }

    @Test
    void migrateShouldNotThrowWhenNoQuotas() throws Exception {
        assertThatCode(() -> Mono.from(testee.changeUsername(BOB, ALICE)).block())
            .doesNotThrowAnyException();

        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(quotaRootResolver.forUser(ALICE))).block())
            .isEqualTo(CurrentQuotas.emptyQuotas());
    }

    @Test
    void migrateShouldSucceedWhenOnlyMessagesQuota() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(10));

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));
        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota().getLimit()).isEqualTo(QuotaCountLimit.count(10));
            softly.assertThat(aliceQuotas.getStorageQuota().getLimit()).isEqualTo(QuotaSizeLimit.unlimited());
        });
    }

    @Test
    void migrateShouldSucceedWhenOnlyStorageQuota() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(10));

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));
        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota().getLimit()).isEqualTo(QuotaCountLimit.unlimited());
            softly.assertThat(aliceQuotas.getStorageQuota().getLimit()).isEqualTo(QuotaSizeLimit.size(10));
        });
    }

    @Test
    void migrateShouldDispatchQuotaUpdateEvent() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(50));
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(100));

        appendAMessage(ALICE);

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        Awaitility.await()
            .atMost(TEN_SECONDS)
            .untilAsserted(() -> assertThat(eventStore).anyMatch(event -> event instanceof MailboxEvents.QuotaUsageUpdatedEvent));

        MailboxEvents.QuotaUsageUpdatedEvent quotaUsageUpdatedEvent = eventStore.stream()
            .filter(event -> event instanceof MailboxEvents.QuotaUsageUpdatedEvent)
            .map(MailboxEvents.QuotaUsageUpdatedEvent.class::cast)
            .reduce((first, second) -> second)
            .orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(quotaUsageUpdatedEvent.getCountQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(1)).computedLimit(QuotaCountLimit.count(50)).build());
            softly.assertThat(quotaUsageUpdatedEvent.getSizeQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(103)).computedLimit(QuotaSizeLimit.size(100)).build());
            softly.assertThat(quotaUsageUpdatedEvent.getUsername())
                .isEqualTo(ALICE);
            softly.assertThat(quotaUsageUpdatedEvent.getQuotaRoot())
                .isEqualTo(quotaRootResolver.forUser(ALICE));
        });
    }

    private void appendAMessage(Username username) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(username);
        MailboxPath inbox = MailboxPath.inbox(username);
        mailboxManager.createMailbox(inbox, session);
        MessageManager messageManager = mailboxManager.getMailbox(inbox, session);
        messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo("test@localhost.com")
                .setBody("This is a message", StandardCharsets.UTF_8)),
            session);
        mailboxManager.endProcessingRequest(session);
    }
}
