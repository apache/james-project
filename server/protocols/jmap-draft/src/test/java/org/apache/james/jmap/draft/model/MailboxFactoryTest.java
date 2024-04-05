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
package org.apache.james.jmap.draft.model;

import static org.apache.james.mailbox.manager.ManagerTestProvisionner.OTHER_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.model.mailbox.MailboxNamespace;
import org.apache.james.jmap.model.mailbox.Rights;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.manager.ManagerTestProvisionner;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.james.jmap.model.Number;

public class MailboxFactoryTest {
    public static final char DELIMITER = '.';

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private StoreMailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private MailboxSession otherMailboxSession;
    private Username user;
    private Username otherUser;
    private MailboxFactory sut;

    @Before
    public void setup() throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        QuotaRootResolver quotaRootResolver = mailboxManager.getQuotaComponents().getQuotaRootResolver();
        QuotaManager quotaManager = mailboxManager.getQuotaComponents().getQuotaManager();

        user = ManagerTestProvisionner.USER;
        otherUser = OTHER_USER;
        mailboxSession = mailboxManager.authenticate(user, ManagerTestProvisionner.USER_PASS).withoutDelegation();
        otherMailboxSession = mailboxManager.authenticate(otherUser, ManagerTestProvisionner.OTHER_USER_PASS).withoutDelegation();
        sut = new MailboxFactory(mailboxManager, quotaManager, quotaRootResolver);
    }


    @Test
    public void mailboxFromMailboxIdShouldReturnAbsentWhenDoesntExist() throws Exception {
        Optional<Mailbox> mailbox = sut.builder()
                .id(InMemoryId.of(123))
                .session(mailboxSession)
                .build()
                .blockOptional();

        assertThat(mailbox).isEmpty();
    }

    @Test
    public void mailboxFromMailboxIdShouldReturnPresentWhenExists() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(user, "myBox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MailboxId mailboxId = mailboxManager.getMailbox(mailboxPath, mailboxSession).getId();

        Optional<Mailbox> mailbox = sut.builder()
                .id(mailboxId)
                .session(mailboxSession)
                .build()
                .blockOptional();

        assertThat(mailbox).isPresent();
        assertThat(mailbox.get().getId()).isEqualTo(mailboxId);
    }

    @Test
    public void getNameShouldReturnMailboxNameWhenRootMailbox() throws Exception {
        String expected = "mailbox";
        MailboxPath mailboxPath = MailboxPath.forUser(user, expected);

        String name = sut.getName(mailboxPath, mailboxSession);
        assertThat(name).isEqualTo(expected);
    }

    @Test
    public void getNameShouldReturnMailboxNameWhenChildMailbox() throws Exception {
        String expected = "mailbox";
        MailboxPath mailboxPath = MailboxPath.forUser(user, "inbox." + expected);

        String name = sut.getName(mailboxPath, mailboxSession);
        assertThat(name).isEqualTo(expected);
    }

    @Test
    public void getNameShouldReturnMailboxNameWhenChildOfChildMailbox() throws Exception {
        String expected = "mailbox";
        MailboxPath mailboxPath = MailboxPath.forUser(user, "inbox.children." + expected);

        String name = sut.getName(mailboxPath, mailboxSession);
        assertThat(name).isEqualTo(expected);
    }

    @Test
    public void getParentIdFromMailboxPathShouldReturNullWhenRootMailbox() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(user, "mailbox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        Optional<MailboxId> id = sut.getParentIdFromMailboxPath(mailboxPath, Optional.empty(), mailboxSession).block();
        assertThat(id).isEmpty();
    }

    @Test
    public void getParentIdFromMailboxPathShouldReturnParentIdWhenChildMailbox() throws Exception {
        MailboxPath parentMailboxPath = MailboxPath.inbox(user);
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxManager.getMailbox(parentMailboxPath, mailboxSession).getId();

        MailboxPath mailboxPath = parentMailboxPath.child("mailbox", '.');
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        Optional<MailboxId> id = sut.getParentIdFromMailboxPath(mailboxPath, Optional.empty(), mailboxSession).block();
        assertThat(id).contains(parentId);
    }

    @Test
    public void getParentIdFromMailboxPathShouldReturnParentIdWhenChildOfChildMailbox() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(user, "inbox.children.mailbox");
        mailboxManager.createMailbox(MailboxPath.forUser(user, "inbox"), mailboxSession);

        MailboxPath parentMailboxPath = MailboxPath.forUser(user, "inbox.children");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxManager.getMailbox(parentMailboxPath, mailboxSession).getId();

        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        Optional<MailboxId> id = sut.getParentIdFromMailboxPath(mailboxPath, Optional.empty(), mailboxSession).block();
        assertThat(id).contains(parentId);
    }

    @Test
    public void getParentIdFromMailboxPathShouldWorkWhenUserMailboxesProvided() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(user, "inbox.children.mailbox");
        mailboxManager.createMailbox(MailboxPath.forUser(user, "inbox"), mailboxSession);

        MailboxPath parentMailboxPath = MailboxPath.forUser(user, "inbox.children");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxManager.getMailbox(parentMailboxPath, mailboxSession).getId();

        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        org.apache.james.mailbox.model.Mailbox mailbox = new org.apache.james.mailbox.model.Mailbox(parentMailboxPath, UidValidity.of(34), parentId);
        Optional<MailboxId> id = sut.getParentIdFromMailboxPath(mailboxPath,
            Optional.of(ImmutableMap.of(parentMailboxPath, new MailboxMetaData(mailbox, DELIMITER,
                MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.NONE, new MailboxACL(),
                MailboxCounters.empty(parentId)))),
            mailboxSession).block();
        assertThat(id).contains(parentId);
    }

    @Test
    public void getNamespaceShouldReturnPersonalNamespaceWhenUserMailboxPathAndUserMailboxSessionAreTheSame() throws Exception {
        MailboxPath inbox = MailboxPath.inbox(user);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);

        Mailbox retrievedMailbox = sut.builder()
            .id(mailboxId.get())
            .session(mailboxSession)
            .build()
            .block();

        assertThat(retrievedMailbox.getNamespace())
            .isEqualTo(MailboxNamespace.personal());
    }

    @Test
    public void buildShouldRelyOnPreloadedMailboxes() throws Exception {
        MailboxPath inbox = MailboxPath.inbox(user);
        Optional<MailboxId> otherId = mailboxManager.createMailbox(inbox.child("child", '.'), mailboxSession);

        InMemoryId preLoadedId = InMemoryId.of(45);
        final org.apache.james.mailbox.model.Mailbox mailbox = new org.apache.james.mailbox.model.Mailbox(inbox, UidValidity.of(34), preLoadedId);
        Mailbox retrievedMailbox = sut.builder()
            .id(otherId.get())
            .session(mailboxSession)
            .usingPreloadedMailboxesMetadata(Optional.of(ImmutableMap.of(inbox, new MailboxMetaData(
                mailbox,
                DELIMITER,
                MailboxMetaData.Children.NO_INFERIORS,
                MailboxMetaData.Selectability.NONE,
                MailboxACL.EMPTY,
                MailboxCounters.empty(preLoadedId)))))
            .build()
            .block();

        assertThat(retrievedMailbox.getParentId())
            .contains(preLoadedId);
    }

    @Test
    public void getNamespaceShouldReturnDelegatedNamespaceWhenUserMailboxPathAndUserMailboxSessionAreNotTheSame() throws Exception {
        MailboxPath inbox = MailboxPath.inbox(user);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);
        mailboxManager.applyRightsCommand(inbox,
            MailboxACL.command()
                .forUser(otherUser)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition(),
            mailboxSession);

        Mailbox retrievedMailbox = sut.builder()
            .id(mailboxId.get())
            .session(otherMailboxSession)
            .build()
            .block();

        assertThat(retrievedMailbox.getNamespace())
            .isEqualTo(MailboxNamespace.delegated(user));
    }

    @Test
    public void ownerShouldHaveFullRightsViaMayProperties() throws Exception {
        MailboxPath inbox = MailboxPath.forUser(user, "inbox");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);

        Mailbox retrievedMailbox = sut.builder()
            .id(mailboxId.get())
            .session(mailboxSession)
            .build()
            .block();

        softly.assertThat(retrievedMailbox.isMayAddItems()).isTrue();
        softly.assertThat(retrievedMailbox.isMayCreateChild()).isTrue();
        softly.assertThat(retrievedMailbox.isMayDelete()).isTrue();
        softly.assertThat(retrievedMailbox.isMayReadItems()).isTrue();
        softly.assertThat(retrievedMailbox.isMayRemoveItems()).isTrue();
        softly.assertThat(retrievedMailbox.isMayRename()).isTrue();
    }

    @Test
    public void delegatedUserShouldHaveMayAddItemsWhenAllowedToInsert() throws Exception {
        MailboxPath inbox = MailboxPath.inbox(user);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);
        mailboxManager.applyRightsCommand(inbox,
            MailboxACL.command()
                .forUser(otherUser)
                .rights(MailboxACL.Right.Insert, MailboxACL.Right.Lookup)
                .asAddition(),
            mailboxSession);

        Mailbox retrievedMailbox = sut.builder()
            .id(mailboxId.get())
            .session(otherMailboxSession)
            .build()
            .block();

        softly.assertThat(retrievedMailbox.isMayAddItems()).isTrue();
        softly.assertThat(retrievedMailbox.isMayCreateChild()).isFalse();
        softly.assertThat(retrievedMailbox.isMayDelete()).isFalse();
        softly.assertThat(retrievedMailbox.isMayReadItems()).isFalse();
        softly.assertThat(retrievedMailbox.isMayRemoveItems()).isFalse();
        softly.assertThat(retrievedMailbox.isMayRename()).isFalse();
    }

    @Test
    public void delegatedUserShouldHaveMayReadItemsWhenAllowedToRead() throws Exception {
        MailboxPath inbox = MailboxPath.inbox(user);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);
        mailboxManager.applyRightsCommand(inbox,
            MailboxACL.command()
                .forUser(otherUser)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition(),
            mailboxSession);

        Mailbox retrievedMailbox = sut.builder()
            .id(mailboxId.get())
            .session(otherMailboxSession)
            .build()
            .block();

        softly.assertThat(retrievedMailbox.isMayAddItems()).isFalse();
        softly.assertThat(retrievedMailbox.isMayCreateChild()).isFalse();
        softly.assertThat(retrievedMailbox.isMayDelete()).isFalse();
        softly.assertThat(retrievedMailbox.isMayReadItems()).isTrue();
        softly.assertThat(retrievedMailbox.isMayRemoveItems()).isFalse();
        softly.assertThat(retrievedMailbox.isMayRename()).isFalse();
    }

    @Test
    public void delegatedUserShouldHaveMayRemoveItemsWhenAllowedToRemoveItems() throws Exception {
        MailboxPath inbox = MailboxPath.inbox(user);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);
        mailboxManager.applyRightsCommand(inbox,
            MailboxACL.command()
                .forUser(otherUser)
                .rights(MailboxACL.Right.DeleteMessages, MailboxACL.Right.Lookup)
                .asAddition(),
            mailboxSession);

        Mailbox retrievedMailbox = sut.builder()
            .id(mailboxId.get())
            .session(otherMailboxSession)
            .build()
            .block();

        softly.assertThat(retrievedMailbox.isMayAddItems()).isFalse();
        softly.assertThat(retrievedMailbox.isMayCreateChild()).isFalse();
        softly.assertThat(retrievedMailbox.isMayDelete()).isFalse();
        softly.assertThat(retrievedMailbox.isMayReadItems()).isFalse();
        softly.assertThat(retrievedMailbox.isMayRemoveItems()).isTrue();
        softly.assertThat(retrievedMailbox.isMayRename()).isFalse();
    }

    @Test
    public void mailboxFromMetaDataShouldReturnPresentStoredValue() throws Exception {
        String name = "myBox";
        MailboxPath mailboxPath = MailboxPath.forUser(user, name);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        mailboxManager.setRights(mailboxPath, MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(OTHER_USER)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read)
                .asAddition()),
            mailboxSession);
        MailboxMetaData metaData = mailboxManager.search(MailboxQuery.privateMailboxesBuilder(mailboxSession).build(), mailboxSession)
            .toStream()
            .filter(metadata -> metadata.getPath().equals(mailboxPath))
            .findFirst()
            .get();

        Optional<Mailbox> mailbox = sut.builder()
            .mailboxMetadata(metaData)
            .session(mailboxSession)
            .build()
            .blockOptional();

        softly.assertThat(mailbox).isPresent();
        softly.assertThat(mailbox).map(Mailbox::getId).contains(metaData.getId());
        softly.assertThat(mailbox).map(Mailbox::getName).contains(name);
        softly.assertThat(mailbox).map(Mailbox::getTotalMessages).contains(Number.ZERO);
        softly.assertThat(mailbox).map(Mailbox::getUnreadMessages).contains(Number.ZERO);
        softly.assertThat(mailbox).map(Mailbox::getSharedWith).contains(new Rights(ImmutableMap.of(
            OTHER_USER, ImmutableList.of(Rights.Right.Lookup, Rights.Right.Read))));
    }

    @Test
    public void buildShouldThrowWhenBothMetadataAndId() {
        assertThatThrownBy(() ->
            sut.builder()
                .session(mailboxSession)
                .id(mock(MailboxId.class))
                .mailboxMetadata(mock(MailboxMetaData.class))
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldThrowWhenNoId() {
        assertThatThrownBy(() ->
            sut.builder()
                .session(mailboxSession)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }
}
