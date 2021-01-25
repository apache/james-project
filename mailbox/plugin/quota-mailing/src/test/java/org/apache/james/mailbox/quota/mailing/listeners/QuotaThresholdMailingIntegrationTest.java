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

import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.BOB_USERNAME;
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

import java.time.Duration;

import org.apache.james.events.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailbox.quota.QuotaFixture.Counts;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.Test;

public interface QuotaThresholdMailingIntegrationTest {
    Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");

    static EventFactory.RequireQuotaCount<EventFactory.RequireQuotaSize<EventFactory.RequireInstant<EventFactory.QuotaUsageUpdatedFinalStage>>> eventBase() {
        return EventFactory.quotaUpdated()
            .eventId(EVENT_ID)
            .user(BOB_USERNAME)
            .quotaRoot(QUOTAROOT);
    }

    @Test
    default void shouldNotSendMailWhenUnderAllThresholds(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailWhenNoThresholdUpdate(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(ONE_HOUR_AGO)
            .build());
        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailWhenThresholdOverPassedRecently(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(TWELVE_HOURS_AGO)
            .build());
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(SIX_HOURS_AGO)
            .build());
        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassed(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendDuplicates(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(ONE_HOUR_AGO)
            .build());

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotifySeparatelyCountAndSize(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(ONE_HOUR_AGO)
            .build());

        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(2);
    }

    @Test
    default void shouldGroupSizeAndCountNotificationsWhenTriggeredByASingleEvent(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassedOverGracePeriod(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(TWELVE_DAYS_AGO)
            .build());
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(SIX_DAYS_AGO)
            .build());
        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendMailWhenNoThresholdUpdateForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(eventBase()
            .quotaCount(Counts._32_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(TWO_DAYS_AGO)
            .build());
        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(TWO_DAYS_AGO)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailWhenThresholdOverPassedRecentlyForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(TWELVE_HOURS_AGO)
            .build());
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(SIX_HOURS_AGO)
            .build());
        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassedForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);

        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(TWELVE_HOURS_AGO)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldSendMailWhenThresholdOverPassedOverGracePeriodForCount(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, DEFAULT_CONFIGURATION);
        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(TWELVE_DAYS_AGO)
            .build());
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(SIX_DAYS_AGO)
            .build());
        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());

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

        testee.event(eventBase()
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());
        testee.event(eventBase()
            .quotaCount(Counts._85_PERCENT)
            .quotaSize(Sizes._42_PERCENT)
            .instant(NOW)
            .build());

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

        ConcurrentTestRunner.builder()
            .operation((threadNb, step) -> testee.event(eventBase()
                    .quotaCount(Counts._40_PERCENT)
                    .quotaSize(Sizes._55_PERCENT)
                    .instant(NOW)
                    .build()))
            .threadCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(mailetContext.getSentMails())
            .hasSize(1);
    }

}