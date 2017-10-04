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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableMap;

public abstract class MailboxMapperACLTest {
    private final static long UID_VALIDITY = 42;
    public static final boolean POSITIVE = true;
    public static final boolean NEGATIVE = !POSITIVE;

    private Mailbox benwaInboxMailbox;

    @Rule
    public ExpectedException expected = ExpectedException.none();
    private MailboxMapper mailboxMapper;
    private MapperProvider mapperProvider;

    protected abstract MapperProvider createMapperProvider();

    public void setUp() throws Exception {
        this.mapperProvider = createMapperProvider();
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.MAILBOX));
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.ACL_STORAGE));

        this.mailboxMapper = mapperProvider.createMailboxMapper();

        MailboxPath benwaInboxPath = MailboxPath.forUser("benwa", "INBOX");
        benwaInboxMailbox = createMailbox(benwaInboxPath);
        mailboxMapper.save(benwaInboxMailbox);
    }

    @Test
    public void storedAclShouldBeEmptyByDefault() throws MailboxException {
        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .isEmpty();
    }

    @Test
    public void updateAclShouldSaveAclWhenReplace() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, rights);
    }

    @Test
    public void updateAclShouldOverwriteStoredAclWhenReplace() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);

        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(newRights).asReplacement());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, newRights);
    }

    @Test
    public void updateAclShouldTreatNegativeAndPositiveRightSeparately() throws MailboxException {
        EntryKey key1 = new EntryKey("user", NameType.user, NEGATIVE);
        EntryKey key2 = new EntryKey("user", NameType.user, POSITIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key1).rights(rights).asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key2).rights(newRights).asReplacement());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(2)
            .containsEntry(key1, rights)
            .containsEntry(key2, newRights);
    }

    @Test
    public void updateAclShouldTreatNameTypesRightSeparately() throws MailboxException {
        EntryKey key1 = new EntryKey("user", NameType.user, NEGATIVE);
        EntryKey key2 = new EntryKey("user", NameType.group, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key1).rights(rights).asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key2).rights(newRights).asReplacement());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(2)
            .containsEntry(key1, rights)
            .containsEntry(key2, newRights);
    }

    @Test
    public void updateAclShouldCleanAclEntryWhenEmptyReplace() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights();
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(newRights).asReplacement());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .isEmpty();
    }

    @Test
    public void updateAclShouldCombineStoredAclWhenAdd() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        Rfc4314Rights bothRights = new Rfc4314Rights(Right.Administer, Right.WriteSeenFlag, Right.PerformExpunge, Right.Write, Right.CreateMailbox, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(newRights).asAddition());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, bothRights);
    }

    @Test
    public void removeAclShouldRemoveSomeStoredAclWhenAdd() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights removedRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.PerformExpunge);
        Rfc4314Rights finalRights = new Rfc4314Rights(Right.Administer, Right.Write);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(removedRights).asRemoval());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, finalRights);
    }

    @Test
    public void removeAclShouldNotFailWhenRemovingNonExistingRight() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights removedRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.PerformExpunge, Right.Lookup);
        Rfc4314Rights finalRights = new Rfc4314Rights(Right.Administer, Right.Write);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(removedRights).asRemoval());

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, finalRights);
    }

    @Test
    public void resetAclShouldReplaceStoredAcl() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge, Right.Write, Right.WriteSeenFlag);
        Rfc4314Rights newRights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement());
        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(ImmutableMap.of(key, newRights)));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, newRights);
    }
    
    @Test
    public void resetAclShouldInitializeStoredAcl() throws MailboxException {
        EntryKey key = new EntryKey("user", NameType.user, NEGATIVE);
        Rfc4314Rights rights = new Rfc4314Rights(Right.WriteSeenFlag, Right.CreateMailbox, Right.Administer, Right.PerformExpunge, Right.DeleteMessages);
        mailboxMapper.setACL(benwaInboxMailbox,
            new MailboxACL(ImmutableMap.of(key, rights)));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, rights);
    }

    private SimpleMailbox createMailbox(MailboxPath mailboxPath) {
        SimpleMailbox mailbox = new SimpleMailbox(mailboxPath, UID_VALIDITY);
        mailbox.setMailboxId(mapperProvider.generateId());
        return mailbox;
    }

    @Test
    public void findMailboxesShouldReturnEmptyWhenNone() throws MailboxException {
        assertThat(mailboxMapper.findMailboxes("user", Right.Administer)).isEmpty();
    }

    @Test
    public void findMailboxesShouldReturnEmptyWhenRightDoesntMatch() throws MailboxException {
        EntryKey key = EntryKey.createUserEntryKey("user");
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(rights)
                .asReplacement());

        assertThat(mailboxMapper.findMailboxes("user", Right.Read)).isEmpty();
    }

    @Test
    public void updateACLShouldInsertUsersRights() throws MailboxException {
        Rfc4314Rights rights = new Rfc4314Rights(Right.Administer, Right.PerformExpunge);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(EntryKey.createUserEntryKey("user"))
                .rights(rights)
                .asAddition());

        assertThat(mailboxMapper.findMailboxes("user", Right.Administer))
            .containsOnly(benwaInboxMailbox);
    }

    @Test
    public void updateACLShouldOverwriteUsersRights() throws MailboxException {
        EntryKey key = EntryKey.createUserEntryKey("user");
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asReplacement());
        Rfc4314Rights newRights = new Rfc4314Rights(Right.Read);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(newRights)
                .asReplacement());

        assertThat(mailboxMapper.findMailboxes("user", Right.Read))
            .containsOnly(benwaInboxMailbox);
    }

    @Test
    public void findMailboxesShouldNotReportDeletedACLViaReplace() throws MailboxException {
        EntryKey key = EntryKey.createUserEntryKey("user");
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .mode(MailboxACL.EditMode.REPLACE)
                .rights(initialRights)
                .build());
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .mode(MailboxACL.EditMode.REPLACE)
                .rights(new Rfc4314Rights())
                .build());

        assertThat(mailboxMapper.findMailboxes("user", Right.Administer))
            .isEmpty();
    }

    @Test
    public void findMailboxesShouldNotReportDeletedACLViaRemove() throws MailboxException {
        EntryKey key = EntryKey.createUserEntryKey("user");
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asReplacement());
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asRemoval());

        assertThat(mailboxMapper.findMailboxes("user", Right.Administer))
            .isEmpty();
    }

    @Test
    public void findMailboxesShouldNotReportDeletedMailboxes() throws MailboxException {
        EntryKey key = EntryKey.createUserEntryKey("user");
        Rfc4314Rights initialRights = new Rfc4314Rights(Right.Administer);
        mailboxMapper.updateACL(benwaInboxMailbox,
            MailboxACL.command()
                .key(key)
                .rights(initialRights)
                .asReplacement());
        mailboxMapper.delete(benwaInboxMailbox);

        assertThat(mailboxMapper.findMailboxes("user", Right.Administer))
            .isEmpty();
    }

    @Test
    public void setACLShouldStoreMultipleUsersRights() throws MailboxException {
        EntryKey user1 = EntryKey.createUserEntryKey("user1");
        EntryKey user2 = EntryKey.createUserEntryKey("user2");

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user1, new Rfc4314Rights(Right.Administer)),
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read))));

        assertThat(mailboxMapper.findMailboxes("user1", Right.Administer))
            .containsOnly(benwaInboxMailbox);
        assertThat(mailboxMapper.findMailboxes("user2", Right.Read))
            .containsOnly(benwaInboxMailbox);
    }

    @Test
    public void findMailboxesShouldNotReportRightsRemovedViaSetAcl() throws MailboxException {
        EntryKey user1 = EntryKey.createUserEntryKey("user1");
        EntryKey user2 = EntryKey.createUserEntryKey("user2");

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user1, new Rfc4314Rights(Right.Administer)),
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read))));

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read))));

        assertThat(mailboxMapper.findMailboxes("user1", Right.Administer))
            .isEmpty();
    }

    @Test
    public void findMailboxesShouldReportRightsUpdatedViaSetAcl() throws MailboxException {
        EntryKey user1 = EntryKey.createUserEntryKey("user1");
        EntryKey user2 = EntryKey.createUserEntryKey("user2");

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user1, new Rfc4314Rights(Right.Administer)),
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Read))));

        mailboxMapper.setACL(benwaInboxMailbox, new MailboxACL(
            new MailboxACL.Entry(user2, new Rfc4314Rights(Right.Write))));

        assertThat(mailboxMapper.findMailboxes("user2", Right.Write))
            .containsOnly(benwaInboxMailbox);
    }

}
