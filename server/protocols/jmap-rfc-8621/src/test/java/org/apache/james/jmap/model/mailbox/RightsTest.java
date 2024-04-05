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

package org.apache.james.jmap.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Username;
import org.apache.james.jmap.model.mailbox.Rights.Right;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RightsTest {

    public static final boolean NEGATIVE = true;
    public static final Username USERNAME = Username.of("user");
    public static final Username OTHER_USERNAME = Username.of("otherUser");

    @Test
    public void rightsShouldMatchBeanContract() {
        EqualsVerifier.forClass(Rights.class)
            .verify();
    }

    @Test
    public void forCharShouldReturnRightWhenA() {
        assertThat(Right.forChar('a'))
            .isEqualTo(Right.Administer);
    }

    @Test
    public void forCharShouldReturnRightWhenE() {
        assertThat(Right.forChar('e'))
            .isEqualTo(Right.Expunge);
    }

    @Test
    public void forCharShouldReturnRightWhenI() {
        assertThat(Right.forChar('i'))
            .isEqualTo(Right.Insert);
    }

    @Test
    public void forCharShouldReturnRightWhenL() {
        assertThat(Right.forChar('l'))
            .isEqualTo(Right.Lookup);
    }

    @Test
    public void forCharShouldReturnRightWhenR() {
        assertThat(Right.forChar('r'))
            .isEqualTo(Right.Read);
    }

    @Test
    public void forCharShouldReturnRightWhenW() {
        assertThat(Right.forChar('w'))
            .isEqualTo(Right.Write);
    }

    @Test
    public void forCharShouldReturnRightWhenT() {
        assertThat(Right.forChar('t'))
            .isEqualTo(Right.DeleteMessages);
    }

    @Test
    public void forCharShouldThrowOnUnsupportedRight() {
        assertThatThrownBy(() -> Right.forChar('k'))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromACLShouldFilterOutGroups() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createGroupEntryKey("group"), Rfc4314Rights.fromSerializedRfc4314Rights("aet")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.EMPTY);
    }

    @Test
    public void fromACLShouldFilterNegatedUsers() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createUserEntryKey(USERNAME, NEGATIVE), Rfc4314Rights.fromSerializedRfc4314Rights("aet")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.EMPTY);
    }

    @Test
    public void fromACLShouldAcceptUsers() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createUserEntryKey(USERNAME), Rfc4314Rights.fromSerializedRfc4314Rights("aet")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.builder()
                .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.DeleteMessages)
                .build());
    }

    @Test
    public void fromACLShouldFilterOutUnknownRights() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createUserEntryKey(USERNAME), Rfc4314Rights.fromSerializedRfc4314Rights("aetpk")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.builder()
                .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.DeleteMessages)
                .build());
    }

    @Test
    public void toMailboxAclShouldReturnEmptyAclWhenEmpty() {
        Rights rights = Rights.EMPTY;

        assertThat(rights.toMailboxAcl())
            .isEqualTo(new MailboxACL());
    }

    @Test
    public void toMailboxAclShouldReturnAclConversion() throws Exception {
        String user1 = "user1";
        String user2 = "user2";
        Rights rights = Rights.builder()
            .delegateTo(Username.of(user1), Right.Administer, Right.DeleteMessages)
            .delegateTo(Username.of(user2), Right.Expunge, Right.Lookup)
            .build();

        assertThat(rights.toMailboxAcl())
            .isEqualTo(new MailboxACL(
                new Entry(user1, MailboxACL.Right.Administer, MailboxACL.Right.DeleteMessages),
                new Entry(user2, MailboxACL.Right.PerformExpunge, MailboxACL.Right.Lookup)));
    }

    @Test
    public void removeEntriesForShouldNotModifyEmptyRights() {
        assertThat(Rights.EMPTY.removeEntriesFor(USERNAME))
            .isEqualTo(Rights.EMPTY);
    }

    @Test
    public void removeEntriesForShouldRemoveUsernameEntryWhenPresent() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Lookup)
            .build();
        assertThat(rights.removeEntriesFor(USERNAME))
            .isEqualTo(Rights.EMPTY);
    }

    @Test
    public void removeEntriesForShouldOnlyRemoveSpecifiedUsername() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Lookup)
            .delegateTo(OTHER_USERNAME, Right.Lookup)
            .build();

        Rights expected = Rights.builder()
            .delegateTo(OTHER_USERNAME, Right.Lookup)
            .build();

        assertThat(rights.removeEntriesFor(USERNAME))
            .isEqualTo(expected);
    }

    @Test
    public void mayAddItemsShouldReturnEmptyWhenNoUserRights() {
        assertThat(Rights.EMPTY.mayAddItems(USERNAME))
            .isEmpty();
    }

    @Test
    public void mayCreateChildShouldReturnUnsupportedWhenNoUserRights() {
        assertThat(Rights.EMPTY.mayCreateChild(USERNAME))
            .isEqualTo(Rights.UNSUPPORTED);
    }

    @Test
    public void mayDeleteShouldReturnUnsupportedWhenNoUserRights() {
        assertThat(Rights.EMPTY.mayDelete(USERNAME))
            .isEqualTo(Rights.UNSUPPORTED);
    }

    @Test
    public void mayReadItemsShouldReturnEmptyWhenNoUserRights() {
        assertThat(Rights.EMPTY.mayReadItems(USERNAME))
            .isEmpty();
    }

    @Test
    public void mayRemoveItemsShouldReturnEmptyWhenNoUserRights() {
        assertThat(Rights.EMPTY.mayRemoveItems(USERNAME))
            .isEmpty();
    }

    @Test
    public void mayRenameShouldReturnEmptyWhenNoUserRights() {
        assertThat(Rights.EMPTY.mayRename(USERNAME))
            .isEmpty();
    }

    @Test
    public void mayAddItemsShouldReturnFalseWhenNoInsertRights() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.Lookup,
                Right.DeleteMessages, Right.Read, Right.Seen, Right.Write)
            .build();

        assertThat(rights.mayAddItems(USERNAME))
            .contains(false);
    }

    @Test
    public void mayCreateChildShouldReturnUnsupportedWhenFullRights() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.Lookup, Right.Insert,
                Right.DeleteMessages, Right.Read, Right.Seen, Right.Write)
            .build();

        assertThat(rights.mayCreateChild(USERNAME))
            .isEqualTo(Rights.UNSUPPORTED);
    }

    @Test
    public void mayDeleteShouldReturnUnsupportedWhenFullRights() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.Lookup, Right.Insert,
                Right.DeleteMessages, Right.Read, Right.Seen, Right.Write)
            .build();

        assertThat(rights.mayDelete(USERNAME))
            .isEqualTo(Rights.UNSUPPORTED);
    }

    @Test
    public void mayReadItemsShouldReturnFalseWhenNoReadRights() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.Lookup, Right.Insert,
                Right.DeleteMessages, Right.Seen, Right.Write)
            .build();

        assertThat(rights.mayReadItems(USERNAME))
            .contains(false);
    }

    @Test
    public void mayRemoveItemsShouldReturnFalseWhenNoDeleteMessagesRights() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.Lookup, Right.Insert,
                Right.Read, Right.Seen, Right.Write)
            .build();

        assertThat(rights.mayRemoveItems(USERNAME))
            .contains(false);
    }

    @Test
    public void mayRenameShouldReturnFalseWhenNoWriteRights() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Administer, Right.Expunge, Right.Lookup, Right.Insert,
                Right.DeleteMessages, Right.Read, Right.Seen)
            .build();

        assertThat(rights.mayRename(USERNAME))
            .isEqualTo(Rights.UNSUPPORTED);
    }

    @Test
    public void mayAddItemsShouldReturnTrueWhenInsertRight() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Insert)
            .build();

        assertThat(rights.mayAddItems(USERNAME))
            .contains(true);
    }

    @Test
    public void mayReadItemsShouldReturnTrueWhenReadRight() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Read)
            .build();

        assertThat(rights.mayReadItems(USERNAME))
            .contains(true);
    }

    @Test
    public void mayRemoveItemsShouldReturnTrueWhenDeleteMessagesRight() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.DeleteMessages)
            .build();

        assertThat(rights.mayRemoveItems(USERNAME))
            .contains(true);
    }

    @Test
    public void mayRenameShouldReturnTrueWhenWriteRight() {
        Rights rights = Rights.builder()
            .delegateTo(USERNAME, Right.Write)
            .build();

        assertThat(rights.mayRename(USERNAME))
            .isEqualTo(Rights.UNSUPPORTED);
    }
}