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

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.junit.Test;

public class PositiveUserACLChangedTest {
    private static final EntryKey ENTRY_KEY = EntryKey.createUserEntryKey("user");
    private static final Rfc4314Rights RIGHTS = new Rfc4314Rights(Right.Administer);

    @Test
    public void addedEntriesShouldReturnEmptyWhenSameACL() {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(positiveUserAclChanged.addedEntries()).isEmpty();
    }

    @Test
    public void removedEntriesShouldReturnEmptyWhenSameACL() {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(positiveUserAclChanged.removedEntries()).isEmpty();
    }

    @Test
    public void changedEntriesShouldReturnEmptyWhenSameACL() {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY);

        assertThat(positiveUserAclChanged.changedEntries()).isEmpty();
    }

    @Test
    public void addedEntriesShouldReturnNewEntryWhenAddedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(positiveUserAclChanged.addedEntries())
            .containsOnly(new Entry(ENTRY_KEY, RIGHTS));
    }

    @Test
    public void changedEntriesShouldReturnEmptyWhenAddedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(positiveUserAclChanged.changedEntries())
            .isEmpty();
    }

    @Test
    public void removedEntriesShouldReturnEmptyWhenAddedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY,
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()));

        assertThat(positiveUserAclChanged.removedEntries())
            .isEmpty();
    }

    @Test
    public void addedEntriesShouldReturnEmptyWhenRemovedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(positiveUserAclChanged.addedEntries())
            .isEmpty();
    }

    @Test
    public void changedEntriesShouldReturnEmptyWhenRemovedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(positiveUserAclChanged.changedEntries())
            .isEmpty();
    }

    @Test
    public void removedEntriesShouldReturnEntryWhenRemovedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition()),
            MailboxACL.EMPTY);

        assertThat(positiveUserAclChanged.removedEntries())
            .containsOnly(new Entry(ENTRY_KEY, RIGHTS));
    }

    @Test
    public void removedEntriesShouldReturnEmptyWhenChangedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
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

        assertThat(positiveUserAclChanged.removedEntries())
            .isEmpty();
    }

    @Test
    public void addedEntriesShouldReturnEmptyWhenChangedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
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

        assertThat(positiveUserAclChanged.addedEntries())
            .isEmpty();
    }

    @Test
    public void changedEntriesShouldReturnEntryWhenChangedEntry() throws Exception {
        PositiveUserACLChanged positiveUserAclChanged = new PositiveUserACLChanged(
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

        assertThat(positiveUserAclChanged.changedEntries())
            .containsOnly(new Entry(ENTRY_KEY, new Rfc4314Rights(MailboxACL.Right.Lookup)));
    }
}