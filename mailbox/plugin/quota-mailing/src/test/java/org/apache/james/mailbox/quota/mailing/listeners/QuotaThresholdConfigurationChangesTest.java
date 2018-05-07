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
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.GRACE_PERIOD;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.NOW;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.QUOTAROOT;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._50;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.mailetContext;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.eventsourcing.EventStore;
import org.apache.james.mailbox.MailboxListener.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.model.QuotaThresholdFixture.Quotas.Counts;
import org.apache.james.mailbox.quota.model.QuotaThresholdFixture.Quotas.Sizes;
import org.apache.james.mailbox.quota.model.QuotaThresholds;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public interface QuotaThresholdConfigurationChangesTest {

    @Test
    default void shouldNotSendMailWhenNoNewExceededThresholdAfterThresholdIncrease(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._55_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailAfterThresholdDecreaseWhenAboveAll(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenNewExceededThresholdAfterThresholdIncrease(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendMailAfterThresholdIncreaseWhenBelowAll(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendMailAfterThresholdDecreaseWhenBelowAll(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._30_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldSendMailWhenNewExceededThresholdAfterThresholdDecrease(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._60_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._60_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldSendEmailWhenAddingANewHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50, _75)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    default void shouldNotSendEmailWhenAddingAHighestNonExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._60_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50, _75)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._60_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenAddingANonHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50, _75)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenRemovingANonHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50, _75)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_75)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenRemovingHighestExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50, _75)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._92_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    default void shouldNotSendEmailWhenRemovingHighestNonExceededThreshold(EventStore store) throws Exception {
        FakeMailContext mailetContext = mailetContext();
        QuotaThresholdListenersTestSystem testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50, _75)), GRACE_PERIOD));

        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._60_PERCENT, NOW));

        testee = new QuotaThresholdListenersTestSystem(mailetContext, store,
            new QuotaMailingListenerConfiguration(new QuotaThresholds(ImmutableList.of(_50)), GRACE_PERIOD));

        mailetContext.resetSentMails();
        testee.event(new QuotaUsageUpdatedEvent(BOB_SESSION, QUOTAROOT, Counts._40_PERCENT, Sizes._60_PERCENT, NOW));

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

}