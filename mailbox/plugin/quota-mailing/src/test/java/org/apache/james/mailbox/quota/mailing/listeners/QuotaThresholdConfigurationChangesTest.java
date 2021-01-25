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
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.GRACE_PERIOD;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.NOW;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.QUOTAROOT;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._50;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.mailetContext;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.events.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailbox.quota.QuotaFixture.Counts;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.Test;

public interface QuotaThresholdConfigurationChangesTest {
    Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");

    QuotaMailingListenerConfiguration CONFIGURATION_50 = QuotaMailingListenerConfiguration.builder()
        .addThreshold(_50)
        .gracePeriod(GRACE_PERIOD)
        .build();
    QuotaMailingListenerConfiguration CONFIGURATION_75 = QuotaMailingListenerConfiguration.builder()
        .addThreshold(_75)
        .gracePeriod(GRACE_PERIOD)
        .build();
    QuotaMailingListenerConfiguration CONFIGURATION_50_75 = QuotaMailingListenerConfiguration.builder()
        .addThresholds(_50, _75)
        .gracePeriod(GRACE_PERIOD)
        .build();

    static EventFactory.RequireQuotaCount<EventFactory.RequireQuotaSize<EventFactory.RequireInstant<EventFactory.QuotaUsageUpdatedFinalStage>>> eventBase() {
        return EventFactory.quotaUpdated()
            .eventId(EVENT_ID)
            .user(BOB_USERNAME)
            .quotaRoot(QUOTAROOT);
    }

    @Test
    default void shouldNotSendMailWhenNoNewExceededThresholdAfterThresholdIncrease(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailAfterThresholdDecreaseWhenAboveAll(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenNewExceededThresholdAfterThresholdIncrease(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendMailAfterThresholdIncreaseWhenBelowAll(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailAfterThresholdDecreaseWhenBelowAll(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        mailetContext.resetSentMails();
        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._30_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenNewExceededThresholdAfterThresholdDecrease(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldSendEmailWhenAddingANewHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            CONFIGURATION_50_75);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendEmailWhenAddingAHighestNonExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            CONFIGURATION_50_75);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenAddingANonHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            CONFIGURATION_50_75);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenRemovingANonHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            CONFIGURATION_50_75);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_75);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenRemovingHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            CONFIGURATION_50_75);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._92_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenRemovingHighestNonExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            CONFIGURATION_50_75);

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(NOW)
            .build());

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store, CONFIGURATION_50);

        mailetContext.resetSentMails();

        testee.event(eventBase()
            .quotaCount(Counts._40_PERCENT)
            .quotaSize(Sizes._60_PERCENT)
            .instant(NOW)
            .build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

}