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

package org.apache.james.mailbox;

import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.Event;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxEvents.MailboxRenamed;
import org.apache.james.mailbox.events.MailboxEvents.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxListenerTest {
    private static final MailboxPath PATH = MailboxPath.forUser(Username.of("bob"), "mailbox");
    private static final MailboxPath OTHER_PATH = MailboxPath.forUser(Username.of("bob"), "mailbox.other");
    private static final Username BOB = Username.of("bob");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final TestId MAILBOX_ID = TestId.of(18);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("bob", Optional.empty());
    private static final QuotaCountUsage QUOTA_COUNT = QuotaCountUsage.count(34);
    private static final QuotaSizeUsage QUOTA_SIZE = QuotaSizeUsage.size(48);
    private static final MailboxACL ACL_1 = new MailboxACL(
        Pair.of(MailboxACL.EntryKey.createUserEntryKey(Username.of("Bob")), new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer)));
    private static final MailboxACL ACL_2 = new MailboxACL(
        Pair.of(MailboxACL.EntryKey.createUserEntryKey(Username.of("Bob")), new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read)));
    private static final MessageUid UID = MessageUid.of(85);
    private static final MessageMetaData META_DATA = new MessageMetaData(UID, ModSeq.of(45), new Flags(), 45, new Date(), Optional.of(new Date()), TestMessageId.of(75), ThreadId.fromBaseMessageId(TestMessageId.of(75)));

    @Test
    void mailboxAddedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxAdded.class).verify();
    }

    @Test
    void mailboxRenamedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxRenamed.class).verify();
    }

    @Test
    void mailboxDeletionShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxDeletion.class).verify();
    }

    @Test
    void mailboxACLUpdatedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxACLUpdated.class).verify();
    }

    @Test
    void addedShouldMatchBeanContract() {
        EqualsVerifier.forClass(Added.class).verify();
    }

    @Test
    void expungedShouldMatchBeanContract() {
        EqualsVerifier.forClass(Expunged.class).verify();
    }

    @Test
    void flagUpdatedShouldMatchBeanContract() {
        EqualsVerifier.forClass(FlagsUpdated.class).verify();
    }

    @Test
    void renameWithSameNameShouldBeNoop() {
        MailboxRenamed mailboxRenamed = new MailboxRenamed(SESSION_ID, BOB, PATH, MAILBOX_ID, PATH,
            Event.EventId.random());

        assertThat(mailboxRenamed.isNoop()).isTrue();
    }

    @Test
    void renameWithDifferentNameShouldNotBeNoop() {
        MailboxRenamed mailboxRenamed = new MailboxRenamed(SESSION_ID, BOB, PATH, MAILBOX_ID, OTHER_PATH,
            Event.EventId.random());

        assertThat(mailboxRenamed.isNoop()).isFalse();
    }

    @Test
    void addedShouldNotBeNoop() {
        MailboxAdded added = new MailboxAdded(SESSION_ID, BOB, PATH, MAILBOX_ID,
            Event.EventId.random());

        assertThat(added.isNoop()).isFalse();
    }

    @Test
    void removedShouldNotBeNoop() {
        MailboxDeletion deletion = new MailboxDeletion(SESSION_ID, BOB, PATH, new MailboxACL(), QUOTA_ROOT,
            QUOTA_COUNT, QUOTA_SIZE, MAILBOX_ID, Event.EventId.random());

        assertThat(deletion.isNoop()).isFalse();
    }

    @Test
    void aclDiffWithSameAclShouldBeNoop() {
        MailboxACLUpdated aclUpdated = new MailboxACLUpdated(SESSION_ID, BOB, PATH, ACLDiff.computeDiff(ACL_1, ACL_1), MAILBOX_ID,
            Event.EventId.random());

        assertThat(aclUpdated.isNoop()).isTrue();
    }

    @Test
    void aclDiffWithDifferentAclShouldNotBeNoop() {
        MailboxACLUpdated aclUpdated = new MailboxACLUpdated(SESSION_ID, BOB, PATH, ACLDiff.computeDiff(ACL_1, ACL_2), MAILBOX_ID,
            Event.EventId.random());

        assertThat(aclUpdated.isNoop()).isFalse();
    }

    @Test
    void addedShouldBeNoopWhenEmpty() {
        Added added = new Added(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of(),
            Event.EventId.random(), !IS_DELIVERY, IS_APPENDED);

        assertThat(added.isNoop()).isTrue();
    }

    @Test
    void addedShouldNotBeNoopWhenNotEmpty() {
        Added added = new Added(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of(UID, META_DATA),
            Event.EventId.random(), !IS_DELIVERY, IS_APPENDED);

        assertThat(added.isNoop()).isFalse();
    }

    @Test
    void expungedShouldBeNoopWhenEmpty() {
        Expunged expunged = new Expunged(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of(),
            Event.EventId.random());

        assertThat(expunged.isNoop()).isTrue();
    }

    @Test
    void expungedShouldNotBeNoopWhenNotEmpty() {
        Expunged expunged = new Expunged(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of(UID, META_DATA),
            Event.EventId.random());

        assertThat(expunged.isNoop()).isFalse();
    }

    @Test
    void flagsUpdatedShouldBeNoopWhenEmpty() {
        FlagsUpdated flagsUpdated = new FlagsUpdated(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableList.of(), Event.EventId.random());

        assertThat(flagsUpdated.isNoop()).isTrue();
    }

    @Test
    void flagsUpdatedShouldNotBeNoopWhenNotEmpty() {
        FlagsUpdated flagsUpdated = new FlagsUpdated(SESSION_ID, BOB, PATH, MAILBOX_ID,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(UID)
                .modSeq(ModSeq.of(45))
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.ANSWERED))
                .build()),
            Event.EventId.random());

        assertThat(flagsUpdated.isNoop()).isFalse();
    }

    @Test
    void quotaUsageUpdatedEventShouldNotBeNoop() {
        QuotaUsageUpdatedEvent event = new QuotaUsageUpdatedEvent(Event.EventId.random(), BOB, QUOTA_ROOT,
            Quota.<QuotaCountLimit, QuotaCountUsage>builder()
                .used(QUOTA_COUNT)
                .computedLimit(QuotaCountLimit.unlimited())
                .build(),
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                .used(QUOTA_SIZE)
                .computedLimit(QuotaSizeLimit.unlimited())
                .build(), Instant.now());

        assertThat(event.isNoop()).isFalse();
    }
}