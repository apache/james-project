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

package org.apache.james.mailbox.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxSubscribedEvent;
import org.apache.james.mailbox.events.MailboxEvents.MailboxUnsubscribedEvent;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

class MailboxSubscriptionEventsTest {
    private static final Username BOB = Username.of("bob");
    private static final MailboxPath PATH = MailboxPath.forUser(BOB, "INBOX");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final TestId MAILBOX_ID = TestId.of(18);
    private static final Event.EventId EVENT_ID = Event.EventId.random();

    private static final MailboxSubscribedEvent SUBSCRIBED = new MailboxSubscribedEvent(SESSION_ID, BOB, PATH, MAILBOX_ID, EVENT_ID);
    private static final MailboxUnsubscribedEvent UNSUBSCRIBED = new MailboxUnsubscribedEvent(SESSION_ID, BOB, PATH, MAILBOX_ID, EVENT_ID);
    private static final MailboxACLUpdated ACL_UPDATED = new MailboxACLUpdated(SESSION_ID, BOB, PATH,
        ACLDiff.computeDiff(MailboxACL.EMPTY, MailboxACL.EMPTY), MAILBOX_ID, EVENT_ID);

    @Test
    void subscribedEventShouldBeEqualToAnEventWithTheSameFields() {
        assertThat(SUBSCRIBED).isEqualTo(new MailboxSubscribedEvent(SESSION_ID, BOB, PATH, MAILBOX_ID, EVENT_ID));
    }

    @Test
    void unsubscribedEventShouldBeEqualToAnEventWithTheSameFields() {
        assertThat(UNSUBSCRIBED).isEqualTo(new MailboxUnsubscribedEvent(SESSION_ID, BOB, PATH, MAILBOX_ID, EVENT_ID));
    }

    @Test
    void subscribedEventShouldNotBeEqualToUnsubscribedEvent() {
        assertThat(SUBSCRIBED).isNotEqualTo(UNSUBSCRIBED);
        assertThat(UNSUBSCRIBED).isNotEqualTo(SUBSCRIBED);
    }

    @Test
    void subscriptionEventsShouldNotBeEqualToACLUpdatedEvent() {
        assertThat(SUBSCRIBED).isNotEqualTo(ACL_UPDATED);
        assertThat(UNSUBSCRIBED).isNotEqualTo(ACL_UPDATED);
    }
}
