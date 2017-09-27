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

    private MailboxPath benwaInboxPath;
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

        benwaInboxPath = new MailboxPath("#private", "benwa", "INBOX");
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
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                rights));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, rights);
    }

    @Test
    public void updateAclShouldOverrightStoredAclWhenReplace() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights newRights = new MailboxACL.Rfc4314Rights("skate");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                newRights));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, newRights);
    }

    @Test
    public void updateAclShouldTreatNegativeAndPositiveRightSeparately() throws MailboxException {
        MailboxACL.EntryKey key1 = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.EntryKey key2 = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, POSITIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights newRights = new MailboxACL.Rfc4314Rights("skate");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key1,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key2,
                MailboxACL.EditMode.REPLACE,
                newRights));

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
        MailboxACL.EntryKey key1 = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.EntryKey key2 = new MailboxACL.EntryKey("user", MailboxACL.NameType.group, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights newRights = new MailboxACL.Rfc4314Rights("skate");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key1,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key2,
                MailboxACL.EditMode.REPLACE,
                newRights));

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
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights newRights = new MailboxACL.Rfc4314Rights("");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                newRights));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .isEmpty();
    }

    @Test
    public void updateAclShouldCombineStoredAclWhenAdd() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights newRights = new MailboxACL.Rfc4314Rights("skate");
        MailboxACL.Rfc4314Rights bothRights = new MailboxACL.Rfc4314Rights("asewkt");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.ADD,
                newRights));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, bothRights);
    }

    @Test
    public void removeAclShouldRemoveSomeStoredAclWhenAdd() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights removedRights = new MailboxACL.Rfc4314Rights("se");
        MailboxACL.Rfc4314Rights finalRights = new MailboxACL.Rfc4314Rights("aw");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REMOVE,
                removedRights));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, finalRights);
    }

    @Test
    public void removeAclShouldNotFailWhenRemovingNonExistingRight() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights removedRights = new MailboxACL.Rfc4314Rights("sel");
        MailboxACL.Rfc4314Rights finalRights = new MailboxACL.Rfc4314Rights("aw");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REMOVE,
                removedRights));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, finalRights);
    }

    @Test
    public void resetAclShouldReplaceStoredAcl() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("asew");
        MailboxACL.Rfc4314Rights newRights = new MailboxACL.Rfc4314Rights("skate");
        mailboxMapper.updateACL(benwaInboxMailbox,
            new MailboxACL.ACLCommand(key,
                MailboxACL.EditMode.REPLACE,
                rights));
        mailboxMapper.resetACL(benwaInboxMailbox,
            new MailboxACL(ImmutableMap.of(key, newRights)));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .getACL()
                .getEntries())
            .hasSize(1)
            .containsEntry(key, newRights);
    }
    
    @Test
    public void resetAclShouldInitializeStoredAcl() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("user", MailboxACL.NameType.user, NEGATIVE);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights("skate");
        mailboxMapper.resetACL(benwaInboxMailbox,
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

}
