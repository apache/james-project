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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxListenerTest {
    private static final MailboxPath PATH = MailboxPath.forUser("bob", "mailbox");
    private static final MailboxPath OTHER_PATH = MailboxPath.forUser("bob", "mailbox.other");
    private static final User BOB = User.fromUsername("bob");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final TestId MAILBOX_ID = TestId.of(18);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("bob", Optional.empty());
    private static final QuotaCount QUOTA_COUNT = QuotaCount.count(34);
    private static final QuotaSize QUOTA_SIZE = QuotaSize.size(48);
    private static final MailboxACL ACL_1 = new MailboxACL(
        Pair.of(MailboxACL.EntryKey.createUserEntryKey("Bob"), new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer)));
    private static final MailboxACL ACL_2 = new MailboxACL(
        Pair.of(MailboxACL.EntryKey.createUserEntryKey("Bob"), new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read)));
    private static final MessageUid UID = MessageUid.of(85);
    private static final MessageMetaData META_DATA = new MessageMetaData(UID, 45, new Flags(), 45, new Date(), TestMessageId.of(75));

    @Test
    void mailboxAddedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxListener.MailboxAdded.class).verify();
    }

    @Test
    void mailboxRenamedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxListener.MailboxRenamed.class).verify();
    }

    @Test
    void mailboxDeletionShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxListener.MailboxDeletion.class).verify();
    }

    @Test
    void mailboxACLUpdatedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxListener.MailboxACLUpdated.class).verify();
    }

    @Test
    void addedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxListener.Added.class).verify();
    }

    @Test
    void expungedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxListener.Expunged.class).verify();
    }

    @Test
    void flagUpdatedShouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxListener.FlagsUpdated.class).verify();
    }

    @Test
    void renameWithSameNameShouldBeNoop() {
        MailboxListener.MailboxRenamed mailboxRenamed = new MailboxListener.MailboxRenamed(SESSION_ID, BOB, PATH, MAILBOX_ID, PATH);

        assertThat(mailboxRenamed.isNoop()).isTrue();
    }

    @Test
    void renameWithDifferentNameShouldNotBeNoop() {
        MailboxListener.MailboxRenamed mailboxRenamed = new MailboxListener.MailboxRenamed(SESSION_ID, BOB, PATH, MAILBOX_ID, OTHER_PATH);

        assertThat(mailboxRenamed.isNoop()).isFalse();
    }

    @Test
    void addedShouldNotBeNoop() {
        MailboxListener.MailboxAdded added = new MailboxListener.MailboxAdded(SESSION_ID, BOB, PATH, MAILBOX_ID);

        assertThat(added.isNoop()).isFalse();
    }

    @Test
    void removedShouldNotBeNoop() {
        MailboxListener.MailboxDeletion deletion = new MailboxListener.MailboxDeletion(SESSION_ID, BOB, PATH, QUOTA_ROOT,
            QUOTA_COUNT, QUOTA_SIZE, MAILBOX_ID);

        assertThat(deletion.isNoop()).isFalse();
    }

    @Test
    void aclDiffWithSameAclShouldBeNoop() {
        MailboxListener.MailboxACLUpdated aclUpdated = new MailboxListener.MailboxACLUpdated(SESSION_ID, BOB, PATH, ACLDiff.computeDiff(ACL_1, ACL_1), MAILBOX_ID);

        assertThat(aclUpdated.isNoop()).isTrue();
    }

    @Test
    void aclDiffWithDifferentAclShouldNotBeNoop() {
        MailboxListener.MailboxACLUpdated aclUpdated = new MailboxListener.MailboxACLUpdated(SESSION_ID, BOB, PATH, ACLDiff.computeDiff(ACL_1, ACL_2), MAILBOX_ID);

        assertThat(aclUpdated.isNoop()).isFalse();
    }

    @Test
    void addedShouldBeNoopWhenEmpty() {
        MailboxListener.Added added = new MailboxListener.Added(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of());

        assertThat(added.isNoop()).isTrue();
    }

    @Test
    void addedShouldNotBeNoopWhenNotEmpty() {
        MailboxListener.Added added = new MailboxListener.Added(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of(UID, META_DATA));

        assertThat(added.isNoop()).isFalse();
    }

    @Test
    void expungedShouldBeNoopWhenEmpty() {
        MailboxListener.Expunged expunged = new MailboxListener.Expunged(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of());

        assertThat(expunged.isNoop()).isTrue();
    }

    @Test
    void expungedShouldNotBeNoopWhenNotEmpty() {
        MailboxListener.Expunged expunged = new MailboxListener.Expunged(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableSortedMap.of(UID, META_DATA));

        assertThat(expunged.isNoop()).isFalse();
    }

    @Test
    void flagsUpdatedShouldBeNoopWhenEmpty() {
        MailboxListener.FlagsUpdated flagsUpdated = new MailboxListener.FlagsUpdated(SESSION_ID, BOB, PATH, MAILBOX_ID, ImmutableList.of());

        assertThat(flagsUpdated.isNoop()).isTrue();
    }

    @Test
    void flagsUpdatedShouldNotBeNoopWhenNotEmpty() {
        MailboxListener.FlagsUpdated flagsUpdated = new MailboxListener.FlagsUpdated(SESSION_ID, BOB, PATH, MAILBOX_ID,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(UID)
                .modSeq(45)
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.ANSWERED))
                .build()));

        assertThat(flagsUpdated.isNoop()).isFalse();
    }

    @Test
    void quotaUsageUpdatedEventShouldNotBeNoop() {
        MailboxListener.QuotaUsageUpdatedEvent event = new MailboxListener.QuotaUsageUpdatedEvent(BOB, QUOTA_ROOT,
            Quota.<QuotaCount>builder()
                .used(QUOTA_COUNT)
                .computedLimit(QuotaCount.unlimited())
                .build(),
            Quota.<QuotaSize>builder()
                .used(QUOTA_SIZE)
                .computedLimit(QuotaSize.unlimited())
                .build(), Instant.now());

        assertThat(event.isNoop()).isFalse();
    }
}