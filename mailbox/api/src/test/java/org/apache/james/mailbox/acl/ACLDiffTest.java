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

package org.apache.james.mailbox.acl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.jupiter.api.Test;

class ACLDiffTest {
    private static final MailboxACL.EntryKey ENTRY_KEY = MailboxACL.EntryKey.createGroupEntryKey("any", false);
    private static final MailboxACL.Rfc4314Rights RIGHTS = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer);

    @Test
    void addedEntriesShouldReturnEmptyWhenSameACL() {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(aclDiff.addedEntries()).isEmpty();
    }

    @Test
    void addedEntriesShouldReturnEmptyWhenSameNonEmptyACL() throws UnsupportedRightException {
        MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(ENTRY_KEY)
                .rights(RIGHTS)
                .asAddition());

        ACLDiff aclDiff = ACLDiff.computeDiff(mailboxACL, mailboxACL);

        assertThat(aclDiff.addedEntries()).isEmpty();
    }

    @Test
    void removedEntriesShouldReturnEmptyWhenSameACL() {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(aclDiff.removedEntries()).isEmpty();
    }

    @Test
    void removedEntriesShouldReturnEmptyWhenSameNonEmptyACL() throws UnsupportedRightException {
        MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(ENTRY_KEY)
                .rights(RIGHTS)
                .asAddition());

        ACLDiff aclDiff = ACLDiff.computeDiff(mailboxACL, mailboxACL);

        assertThat(aclDiff.removedEntries()).isEmpty();
    }

    @Test
    void changedEntriesShouldReturnEmptyWhenSameACL() {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(aclDiff.changedEntries()).isEmpty();
    }

    @Test
    void changedEntriesShouldReturnEmptyWhenSameNonEmptyACL() throws UnsupportedRightException {
        MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(ENTRY_KEY)
                .rights(RIGHTS)
                .asAddition());

        ACLDiff acldiff = ACLDiff.computeDiff(mailboxACL, mailboxACL);

        assertThat(acldiff.changedEntries()).isEmpty();
    }
    
    @Test
    void addedEntriesShouldReturnNewEntryWhenAddedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(aclDiff.addedEntries())
            .containsOnly(new MailboxACL.Entry(ENTRY_KEY, RIGHTS));
    }

    @Test
    void changedEntriesShouldReturnEmptyWhenAddedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(aclDiff.changedEntries())
            .isEmpty();
    }

    @Test
    void removedEntriesShouldReturnEmptyWhenAddedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(aclDiff.removedEntries())
            .isEmpty();
    }

    @Test
    void addedEntriesShouldReturnEmptyWhenRemovedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(aclDiff.addedEntries())
            .isEmpty();
    }

    @Test
    void changedEntriesShouldReturnEmptyWhenRemovedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(aclDiff.changedEntries())
            .isEmpty();
    }

    @Test
    void removedEntriesShouldReturnEntryWhenRemovedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(aclDiff.removedEntries())
            .containsOnly(new MailboxACL.Entry(ENTRY_KEY, RIGHTS));
    }

    @Test
    void removedEntriesShouldReturnEmptyWhenChangedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()));

        assertThat(aclDiff.removedEntries())
            .isEmpty();
    }

    @Test
    void addedEntriesShouldReturnEmptyWhenChangedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()));

        assertThat(aclDiff.addedEntries())
            .isEmpty();
    }

    @Test
    void changedEntriesShouldReturnEntryWhenChangedEntry() throws Exception {
        ACLDiff aclDiff = ACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(MailboxACL.Right.Administer)
                    .asAddition()),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()));

        assertThat(aclDiff.changedEntries())
            .containsOnly(new MailboxACL.Entry(ENTRY_KEY, new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup)));
    }

}