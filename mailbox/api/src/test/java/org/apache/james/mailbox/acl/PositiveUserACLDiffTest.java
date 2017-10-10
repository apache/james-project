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
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.junit.Test;

public class PositiveUserACLDiffTest {

    private static final EntryKey ENTRY_KEY = EntryKey.createUserEntryKey("user");
    private static final Rfc4314Rights RIGHTS = new Rfc4314Rights(Right.Administer);

    @Test
    public void addedEntriesShouldReturnEmptyWhenSameACL() {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(positiveUserAclDiff.addedEntries()).isEmpty();
    }

    @Test
    public void addedEntriesShouldReturnEmptyWhenSameNonEmptyACL() throws UnsupportedRightException {
        MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(ENTRY_KEY)
                .rights(RIGHTS)
                .asAddition());

        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(mailboxACL, mailboxACL);

        assertThat(positiveUserAclDiff.addedEntries()).isEmpty();
    }

    @Test
    public void removedEntriesShouldReturnEmptyWhenSameACL() {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(positiveUserAclDiff.removedEntries()).isEmpty();
    }

    @Test
    public void removedEntriesShouldReturnEmptyWhenSameNonEmptyACL() throws UnsupportedRightException {
        MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(ENTRY_KEY)
                .rights(RIGHTS)
                .asAddition());

        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(mailboxACL, mailboxACL);

        assertThat(positiveUserAclDiff.removedEntries()).isEmpty();
    }

    @Test
    public void changedEntriesShouldReturnEmptyWhenSameACL() {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(positiveUserAclDiff.changedEntries()).isEmpty();
    }

    @Test
    public void changedEntriesShouldReturnEmptyWhenSameNonEmptyACL() throws UnsupportedRightException {
        MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(ENTRY_KEY)
                .rights(RIGHTS)
                .asAddition());

        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(mailboxACL, mailboxACL);

        assertThat(positiveUserAclDiff.changedEntries()).isEmpty();
    }
    @Test
    public void addedEntriesShouldReturnNewEntryWhenAddedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(positiveUserAclDiff.addedEntries())
            .containsOnly(new Entry(ENTRY_KEY, RIGHTS));
    }

    @Test
    public void changedEntriesShouldReturnEmptyWhenAddedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(positiveUserAclDiff.changedEntries())
            .isEmpty();
    }

    @Test
    public void removedEntriesShouldReturnEmptyWhenAddedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(positiveUserAclDiff.removedEntries())
            .isEmpty();
    }

    @Test
    public void addedEntriesShouldReturnEmptyWhenRemovedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(positiveUserAclDiff.addedEntries())
            .isEmpty();
    }

    @Test
    public void changedEntriesShouldReturnEmptyWhenRemovedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(positiveUserAclDiff.changedEntries())
            .isEmpty();
    }

    @Test
    public void removedEntriesShouldReturnEntryWhenRemovedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(positiveUserAclDiff.removedEntries())
            .containsOnly(new Entry(ENTRY_KEY, RIGHTS));
    }

    @Test
    public void removedEntriesShouldReturnEmptyWhenChangedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(Right.Lookup)
                    .asAddition()));

        assertThat(positiveUserAclDiff.removedEntries())
            .isEmpty();
    }

    @Test
    public void addedEntriesShouldReturnEmptyWhenChangedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(Right.Lookup)
                    .asAddition()));

        assertThat(positiveUserAclDiff.addedEntries())
            .isEmpty();
    }

    @Test
    public void changedEntriesShouldReturnEntryWhenChangedEntry() throws Exception {
        PositiveUserACLDiff positiveUserAclDiff = PositiveUserACLDiff.computeDiff(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(Right.Administer)
                    .asAddition()),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(Right.Lookup)
                    .asAddition()));

        assertThat(positiveUserAclDiff.changedEntries())
            .containsOnly(new Entry(ENTRY_KEY, new Rfc4314Rights(MailboxACL.Right.Lookup)));
    }
}
