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

package org.apache.james.transport.mailets;

import static org.apache.james.transport.mailets.WithStorageDirectiveTest.NO_DOMAIN_LIST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class SubAddressingTest {

    private static final String SENDER1 = "sender1@localhost";
    private static final String RECIPIENT = "recipient@localhost";
    private static final String TARGET = "targetfolder";
    private static final String TARGET_UPPERCASE = "Targetfolder";
    private static final String TARGET_UNEXISTING = "unexistingfolder";

    private MailboxManager mailboxManager;
    private SubAddressing testee;
    private UsersRepository usersRepository;
    private Username senderUsername;
    private Username recipientUsername;
    private MailboxSession recipientSession;
    private MailboxId targetMailboxId;

    @BeforeEach
    void setUp() throws MailboxException, MessagingException, UsersRepositoryException {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        usersRepository = MemoryUsersRepository.withVirtualHosting(NO_DOMAIN_LIST);

        recipientUsername = usersRepository.getUsername(new MailAddress(RECIPIENT));
        senderUsername = usersRepository.getUsername(new MailAddress(SENDER1));

        recipientSession = mailboxManager.createSystemSession(recipientUsername);
        targetMailboxId = mailboxManager.createMailbox(
            MailboxPath.forUser(recipientUsername, TARGET), recipientSession).get();

        testee = new SubAddressing(usersRepository, mailboxManager);
        testee.init(FakeMailetConfig.builder().build());
    }

    @Test
    void shouldNotAddStorageDirectiveWhenTargetMailboxDoesNotExist() throws Exception {
        Mail mail = mailBuilder(TARGET_UNEXISTING).sender(SENDER1).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .doesNotContain(Pair.of(recipient, TARGET_UNEXISTING));
    }

    @Test
    void shouldAddStorageDirectiveWhenTargetMailboxExistsButLowerCase() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET_UPPERCASE).sender(SENDER1).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
                .containsOnly(Pair.of(recipient, TARGET));
    }

    @Test
    void shouldAddStorageDirectiveWhenTargetMailboxExistsButUpperCase() throws Exception {
        targetMailboxId = mailboxManager.createMailbox(
                MailboxPath.forUser(recipientUsername, "Target"), recipientSession).get();

        MailboxACL.ACLCommand command = MailboxACL.command()
                .key(MailboxACL.ANYONE_KEY)
                .rights(MailboxACL.Right.Post)
                .asAddition();
        mailboxManager.applyRightsCommand(targetMailboxId, command, recipientSession);

        Mail mail = mailBuilder("target").sender(SENDER1).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
                .containsOnly(Pair.of(recipient, "Target"));
    }

    @Test
    void shouldAddStorageDirectiveWhenTargetFolderIsASubFolder() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        MailboxPath newPath = MailboxPath.forUser(recipientUsername, "folder" + recipientSession.getPathDelimiter() + TARGET);
        mailboxManager.renameMailbox(targetMailboxId, newPath, recipientSession).getFirst().getMailboxId();

        Mail mail = mailBuilder("folder" + recipientSession.getPathDelimiter() + TARGET).sender(SENDER1).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
                .containsOnly(Pair.of(recipient, "folder" + recipientSession.getPathDelimiter() + TARGET));
    }

    @Test
    void shouldAddStorageDirectiveWhenTargetFolderIsASubFolderWithDifferentCase() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        MailboxPath newPath = MailboxPath.forUser(recipientUsername, "Folder" + recipientSession.getPathDelimiter() + TARGET_UPPERCASE);
        mailboxManager.renameMailbox(targetMailboxId, newPath, recipientSession).getFirst().getMailboxId();

        Mail mail = mailBuilder("folder" + recipientSession.getPathDelimiter() + TARGET).sender(SENDER1).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
                .containsOnly(Pair.of(recipient, "Folder" + recipientSession.getPathDelimiter() + TARGET_UPPERCASE));
    }

    @Test
    void shouldNotAddStorageDirectiveWhenNobodyHasRight() throws Exception {
        removePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET).sender(SENDER1).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .doesNotContain(Pair.of(recipient, TARGET));
    }


    @Test
    void shouldAddStorageDirectiveWhenAnyoneHasRight() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET).sender(SENDER1).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(recipient, TARGET));
    }

    @Test
    void shouldAddStorageDirectiveWhenAnyoneHasRightAndSenderIsUnknown() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(recipient, TARGET));
    }

    @Test
    void shouldNotAddStorageDirectiveWhenNobodyHasRightAndSenderIsUnknown() throws Exception {
        removePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET).build();
        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .doesNotContain(Pair.of(recipient, TARGET));
    }

    private FakeMail.Builder mailBuilder(String targetFolder) throws MessagingException {
        return FakeMail.builder()
            .name("name")
            .recipient("recipient+" + targetFolder + "@localhost");
    }

    private void givePostRightForKey(MailboxACL.EntryKey key) throws MailboxException {
        MailboxACL.ACLCommand command = MailboxACL.command()
            .key(key)
            .rights(MailboxACL.Right.Post)
            .asAddition();

        mailboxManager.applyRightsCommand(targetMailboxId, command, recipientSession);
    }

    private void removePostRightForKey(MailboxACL.EntryKey key) throws MailboxException {
        MailboxACL.ACLCommand command = MailboxACL.command()
            .key(key)
            .rights(MailboxACL.Right.Post)
            .asRemoval();

        mailboxManager.applyRightsCommand(targetMailboxId, command, recipientSession);
    }

    Pair<AttributeName, String> unbox(Attribute attribute) {
        Collection<AttributeValue> collection = (Collection<AttributeValue>) attribute.getValue().getValue();
        return Pair.of(attribute.getName(), (String) collection.stream().findFirst().get().getValue());
    }
}
