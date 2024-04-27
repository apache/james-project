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
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class QuotaUsernameChangeTaskStepTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");

    private QuotaUsernameChangeTaskStep testee;
    private QuotaManager quotaManager;
    private CurrentQuotaManager currentQuotaManager;
    private UserQuotaRootResolver quotaRootResolver;
    private MaxQuotaManager maxQuotaManager;

    private ArrayList<Event> eventStore;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
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

        testee = new QuotaUsernameChangeTaskStep(
            quotaManager,
            currentQuotaManager,
            quotaRootResolver,
            maxQuotaManager,
            eventBus);
    }

    @Test
    void shouldMigrateQuotas() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(50));
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(100));
        Mono.from(currentQuotaManager.setCurrentQuotas(QuotaOperation.from(bobQuotaRoot, new CurrentQuotas(
            QuotaCountUsage.count(5), QuotaSizeUsage.size(10)
        )))).block();

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));

        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(5)).computedLimit(QuotaCountLimit.count(50)).build());

            softly.assertThat(aliceQuotas.getStorageQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(10)).computedLimit(QuotaSizeLimit.size(100)).build());
        });
    }

    @Test
    void migrateShouldNotThrowWhenNoQuotas() throws Exception {
        assertThatCode(() -> Mono.from(testee.changeUsername(BOB, ALICE)).block())
            .doesNotThrowAnyException();
        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));
        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(0)).computedLimit(QuotaCountLimit.unlimited()).build());

            softly.assertThat(aliceQuotas.getStorageQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(0)).computedLimit(QuotaSizeLimit.unlimited()).build());
        });
    }

    @Test
    void migrateShouldSucceedWhenUnlimitedQuotas() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.unlimited());
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.unlimited());
        Mono.from(currentQuotaManager.setCurrentQuotas(QuotaOperation.from(bobQuotaRoot, new CurrentQuotas(
            QuotaCountUsage.count(5), QuotaSizeUsage.size(10)
        )))).block();

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));
        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(5)).computedLimit(QuotaCountLimit.unlimited()).build());

            softly.assertThat(aliceQuotas.getStorageQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(10)).computedLimit(QuotaSizeLimit.unlimited()).build());
        });
    }

    @Test
    void migrateShouldSucceedWhenOnlyMessagesQuota() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(10));
        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));
        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(0)).computedLimit(QuotaCountLimit.count(10)).build());

            softly.assertThat(aliceQuotas.getStorageQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(0)).computedLimit(QuotaSizeLimit.unlimited()).build());
        });
    }

    @Test
    void migrateShouldSucceedWhenOnlyStorageQuota() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(10));
        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));
        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(0)).computedLimit(QuotaCountLimit.unlimited()).build());

            softly.assertThat(aliceQuotas.getStorageQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(0)).computedLimit(QuotaSizeLimit.size(10)).build());
        });
    }

    @Test
    void migrateShouldSucceedWhenAliceAlreadyQuotas() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(50));
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(100));
        Mono.from(currentQuotaManager.setCurrentQuotas(QuotaOperation.from(bobQuotaRoot, new CurrentQuotas(
            QuotaCountUsage.count(5), QuotaSizeUsage.size(10)
        )))).block();

        QuotaRoot aliceQuotaRoot = quotaRootResolver.forUser(ALICE);
        maxQuotaManager.setMaxMessage(aliceQuotaRoot, QuotaCountLimit.count(55));
        maxQuotaManager.setMaxStorage(aliceQuotaRoot, QuotaSizeLimit.size(150));
        Mono.from(currentQuotaManager.setCurrentQuotas(QuotaOperation.from(aliceQuotaRoot, new CurrentQuotas(
            QuotaCountUsage.count(7), QuotaSizeUsage.size(8)
        )))).block();

        Mono.from(testee.changeUsername(BOB, ALICE)).block();

        QuotaManager.Quotas aliceQuotas = quotaManager.getQuotas(quotaRootResolver.forUser(ALICE));

        assertSoftly(softly -> {
            softly.assertThat(aliceQuotas.getMessageQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(5)).computedLimit(QuotaCountLimit.count(50)).build());

            softly.assertThat(aliceQuotas.getStorageQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(10)).computedLimit(QuotaSizeLimit.size(100)).build());
        });
    }

    @Test
    void migrateShouldDispatchQuotaUpdateEvent() throws Exception {
        QuotaRoot bobQuotaRoot = quotaRootResolver.forUser(BOB);
        maxQuotaManager.setMaxMessage(bobQuotaRoot, QuotaCountLimit.count(50));
        maxQuotaManager.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(100));
        Mono.from(currentQuotaManager.setCurrentQuotas(QuotaOperation.from(bobQuotaRoot, new CurrentQuotas(
            QuotaCountUsage.count(5), QuotaSizeUsage.size(10)
        )))).block();

        Mono.from(testee.changeUsername(BOB, ALICE)).block();


        Awaitility.await()
            .atMost(TEN_SECONDS)
            .untilAsserted(() -> assertThat(eventStore.size()).isEqualTo(1));

        MailboxEvents.QuotaUsageUpdatedEvent quotaUsageUpdatedEvent = (MailboxEvents.QuotaUsageUpdatedEvent) eventStore.get(0);

        assertSoftly(softly -> {
            softly.assertThat(quotaUsageUpdatedEvent.getCountQuota())
                .isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(5)).computedLimit(QuotaCountLimit.count(50)).build());
            softly.assertThat(quotaUsageUpdatedEvent.getSizeQuota())
                .isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(10)).computedLimit(QuotaSizeLimit.size(100)).build());
            softly.assertThat(quotaUsageUpdatedEvent.getUsername())
                .isEqualTo(ALICE);
            softly.assertThat(quotaUsageUpdatedEvent.getQuotaRoot())
                .isEqualTo(quotaRootResolver.forUser(ALICE));
        });
    }
}