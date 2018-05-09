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

package org.apache.james.mailbox.quota.mailing.listeners;

import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.BOB_SESSION;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.DEFAULT_CONFIGURATION;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.GRACE_PERIOD;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.NOW;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.ONE_HOUR_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.QUOTAROOT;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.SIX_DAYS_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.SIX_HOURS_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.TWELVE_DAYS_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.TWELVE_HOURS_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.TWO_DAYS_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._50;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._80;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.mailetContext;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.james.eventsourcing.EventStore;
import org.apache.james.mailbox.MailboxListener.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.model.QuotaThresholdFixture.Quotas.Counts;
import org.apache.james.mailbox.quota.model.QuotaThresholdFixture.Quotas.Sizes;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.Test;

public interface QuotaThresholdMailingIntegrationTest {

    @Test
    default void shouldNotSendMailWhenUnderAllThresholds(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailWhenNoThresholdUpdate(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, ONE_HOUR_AGO));
        mailetContext.resetSentMails();

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailWhenThresholdOverPassedRecently(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, TWELVE_HOURS_AGO));
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, SIX_HOURS_AGO));
        mailetContext.resetSentMails();

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassed(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendDuplicates(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, ONE_HOUR_AGO));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotifySeparatelyCountAndSize(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, ONE_HOUR_AGO));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._60_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(2);
    }

    @Test
    default void shouldGroupSizeAndCountNotificationsWhenTriggeredByASingleEvent(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._55_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassedOverGracePeriod(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, TWELVE_DAYS_AGO));
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, SIX_DAYS_AGO));
        mailetContext.resetSentMails();

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendMailWhenNoThresholdUpdateForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._32_PERCENT, Sizes._55_PERCENT, TWO_DAYS_AGO));
        mailetContext.resetSentMails();

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._60_PERCENT, TWO_DAYS_AGO));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailWhenThresholdOverPassedRecentlyForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._30_PERCENT, TWELVE_HOURS_AGO));
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, SIX_HOURS_AGO));
        mailetContext.resetSentMails();

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._30_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassedForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._30_PERCENT, TWELVE_HOURS_AGO));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassedOverGracePeriodForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._30_PERCENT, TWELVE_DAYS_AGO));
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, SIX_DAYS_AGO));
        mailetContext.resetSentMails();

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._30_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldSendOneNoticePerThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            QuotaMailingListenerConfiguration.builder()
                .addThresholds(_50, _80)
                .gracePeriod(GRACE_PERIOD)
                .build());

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._52_PERCENT, Sizes._30_PERCENT, NOW));
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._85_PERCENT, Sizes._42_PERCENT, NOW));

        assertThat(mailetContext.getSentMails())
            .hasSize(2);
    }

    @Test
    default void shouldSendOneMailUponConcurrentEvents(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            QuotaMailingListenerConfiguration.builder()
                .addThresholds(_50, _80)
                .gracePeriod(GRACE_PERIOD)
                .build());

        new ConcurrentTestRunner(10, 1, (threadNb, step) ->
            testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW)))
            .run()
            .awaitTermination(1, TimeUnit.MINUTES);

        assertThat(mailetContext.getSentMails())
            .hasSize(1);
    }

}