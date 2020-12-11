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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

public abstract class MailboxMapperACLTest {
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);
    private static final boolean POSITIVE = true;
    private static final boolean NEGATIVE = !POSITIVE;
    private static final Username USER = Username.of("user");
    private static final Username USER_1 = Username.of("user1");
    private static final Username USER_2 = Username.of("user2");

    private Mailbox benwaInboxMailbox;

    private MailboxMapper mailboxMapper;

    protected abstract MailboxMapper createMailboxMapper();

    @BeforeEach
    void setUp() {
        mailboxMapper = createMailboxMapper();
        MailboxPath benwaInboxPath = MailboxPath.forUser(Username.of("benwa"), "INBOX");
        benwaInboxMailbox = mailboxMapper.create(benwaInboxPath, UID_VALIDITY).block();
    }

    @Test
    void storedAclShouldBeEmptyByDefault() {
        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .isEmpty();
    }

    @Test
    void updateAclShouldSaveAclWhenReplace() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, rights);
    }

    @Test
    void updateAclShouldOverwriteStoredAclWhenReplace() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);

        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(newRights).asReplacement()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, newRights);
    }

    @Test
    void updateAclShouldTreatNegativeAndPositiveRightSeparately() {
        EntryKey key1 = EntryKey.createUserEntryKey(USER, NEGATIVE);
        EntryKey key2 = EntryKey.createUserEntryKey(USER, POSITIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key1).rights(rights).asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key2).rights(newRights).asReplacement()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(2)
            .containsEntry(key1, rights)
            .containsEntry(key2, newRights);
    }

    @Test
    void updateAclShouldTreatNameTypesRightSeparately() {
        EntryKey key1 = EntryKey.createUserEntryKey(USER);
        EntryKey key2 = EntryKey.createGroupEntryKey(USER.asString());
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key1).rights(rights).asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key2).rights(newRights).asReplacement()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(2)
            .containsEntry(key1, rights)
            .containsEntry(key2, newRights);
    }

    @Test
    void updateAclShouldCleanAclEntryWhenEmptyReplace() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(newRights).asReplacement()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .isEmpty();
    }

    @Test
    protected void updateAclShouldCombineStoredAclWhenAdd() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        Rfc4314Rights bothRights = new Rfc4314Rights(Right.Administer, Right.WriteSeenFlag, Right.PerformExpunge, Right.Write, Right.CreateMailbox, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(newRights).asAddition()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, bothRights);
    }

    @Test
    void removeAclShouldRemoveSomeStoredAclWhenAdd() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights removedRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.PerformExpunge);
        Rfc4314Rights finalRights = new Rfc4314Rights(Right.Administer, Right.Write);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(removedRights).asRemoval()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, finalRights);
    }

    @Test
    void removeAclShouldNotFailWhenRemovingNonExistingRight() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights removedRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.PerformExpunge, Right.Lookup);
        Rfc4314Rights finalRights = new Rfc4314Rights(Right.Administer, Right.Write);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(removedRights).asRemoval()).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, finalRights);
    }

    @Test
    void resetAclShouldReplaceStoredAcl() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();
        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(ImmutableMap.of(key, newRights))).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, newRights);
    }
    
    @Test
    void resetAclShouldInitializeStoredAcl() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.setACL(benwaInboxMailbox,
            new MailboxACL(ImmutableMap.of(key, rights))).block();

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, rights);
    }

    @Test
    void findMailboxesShouldReturnEmptyWhenNone() {
        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Administer).collectList().block())
            .isEmpty();
    }

    @Test
    void findMailboxesShouldReturnEmptyWhenRightDoesntMatch() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(rights)
                .asReplacement()).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Read).collectList().block())
            .isEmpty();
    }

    @Test
    void updateACLShouldInsertUsersRights() {
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(EntryKey.createUserEntryKey(USER))
                .rights(rights)
                .asAddition()).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Administer).collectList().block())
            .containsOnly(benwaInboxMailbox);
    }

    @Test
    void updateACLShouldOverwriteUsersRights() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asReplacement()).block();
        Rfc4314Rights newRights = new Rfc4314Rights(Right.Read);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(newRights)
                .asReplacement()).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Read).collectList().block())
            .containsOnly(benwaInboxMailbox);

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Administer).collectList().block())
            .isEmpty();
    }

    @Test
    void findMailboxesShouldNotReportDeletedACLViaReplace() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .mode(MailboxACL.EditMode.REPLACE)
                .rights(initialRights)
                .build()).block();
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .mode(MailboxACL.EditMode.REPLACE)
                .rights(new Rfc4314Rights())
                .build()).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Administer).collectList().block())
            .isEmpty();
    }

    @Test
    void findMailboxesShouldNotReportDeletedACLViaRemove() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asReplacement()).block();
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asRemoval()).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Administer).collectList().block())
            .isEmpty();
    }

    @Test
    void findMailboxesShouldNotReportDeletedMailboxes() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asReplacement()).block();
        mailboxMapper.delete(benwaInboxMailbox).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER, Right.Administer).collectList().block())
            .isEmpty();
    }

    @Test
    void setACLShouldStoreMultipleUsersRights() {
        EntryKey user1 = EntryKey.createUserEntryKey(USER_1);
        EntryKey user2 = EntryKey.createUserEntryKey(USER_2);

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user1, new Rfc4314Rights(Right.Administer)),
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read)))).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER_1, Right.Administer).collectList().block())
            .containsOnly(benwaInboxMailbox);
        assertThat(mailboxMapper.findNonPersonalMailboxes(USER_2, Right.Read).collectList().block())
            .containsOnly(benwaInboxMailbox);
    }

    @Test
    void findMailboxesShouldNotReportRightsRemovedViaSetAcl() {
        EntryKey user1 = EntryKey.createUserEntryKey(USER_1);
        EntryKey user2 = EntryKey.createUserEntryKey(USER_2);

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user1, new Rfc4314Rights(Right.Administer)),
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read)))).block();

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read)))).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER_1, Right.Administer).collectList().block())
            .isEmpty();
    }

    @Test
    void findMailboxesShouldReportRightsUpdatedViaSetAcl() {
        EntryKey user1 = EntryKey.createUserEntryKey(USER_1);
        EntryKey user2 = EntryKey.createUserEntryKey(USER_2);

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user1, new Rfc4314Rights(Right.Administer)),
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read)))).block();

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Write)))).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(USER_2, Right.Write).collectList().block())
            .containsOnly(benwaInboxMailbox);
    }

    @Test
    void findMailboxByPathShouldReturnMailboxWithACL() {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.setACL(benwaInboxMailbox,
            new MailboxACL(ImmutableMap.of(key, rights))).block();

        assertThat(
            mailboxMapper.findMailboxByPath(benwaInboxMailbox.generateAssociatedPath())
                .block()
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, rights);
    }

    @Test
    void setACLShouldReturnACLDiff() throws MailboxException {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);

        ACLDiff expectAclDiff = ACLDiff.computeDiff(MailboxACL.EMPTY, MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(key)
                .rights(rights)
                .asAddition()));

        assertThat(mailboxMapper.setACL(benwaInboxMailbox,
            new MailboxACL(ImmutableMap.of(key, rights))).block()).isEqualTo(expectAclDiff);
    }

    @Test
    void updateACLShouldReturnACLDiff() throws MailboxException {
        EntryKey key = EntryKey.createUserEntryKey(USER);
        Rfc4314Rights rights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);

        MailboxACL.ACLCommand aclCommand = MailboxACL.command()
            .key(key)
            .rights(rights)
            .asAddition();

        ACLDiff expectAclDiff = ACLDiff.computeDiff(MailboxACL.EMPTY, MailboxACL.EMPTY.apply(aclCommand));

        assertThat(mailboxMapper.updateACL(benwaInboxMailbox, aclCommand).block()).isEqualTo(expectAclDiff);
    }
}
