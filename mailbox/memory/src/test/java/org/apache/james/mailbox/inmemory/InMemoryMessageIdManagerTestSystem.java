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
package org.apache.james.mailbox.inmemory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageWriter;

import com.google.common.base.Throwables;

public class InMemoryMessageIdManagerTestSystem extends MessageIdManagerTestSystem {

    public static InMemoryMessageIdManagerTestSystem create() throws MailboxException {
        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        StoreMailboxManager mailboxManager = inMemoryIntegrationResources.createMailboxManager(new SimpleGroupMembershipResolver());
        return new InMemoryMessageIdManagerTestSystem(
            inMemoryIntegrationResources.createMessageIdManager(mailboxManager),
            mailboxManager);
    }

    private static final MessageId FIRST_MESSAGE_ID = InMemoryMessageId.of(1);
    private static final long ONE_HUNDRED = 100;
    private static final int UID_VALIDITY = 1024;

    private final MailboxManager mailboxManager;
    private Optional<MessageId> lastMessageIdUsed;
    private final Message message;

    private InMemoryMessageIdManagerTestSystem(MessageIdManager messageIdManager, MailboxManager mailboxManager) {
        super(messageIdManager);
        this.mailboxManager = mailboxManager;
        this.lastMessageIdUsed = Optional.empty();
        try {
            this.message = Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)
                .build();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Mailbox createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        return new SimpleMailbox(mailboxPath, UID_VALIDITY, messageManager.getId());
    }

    @Override
    public MessageId persist(MailboxId mailboxId, MessageUid uid, Flags flags, MailboxSession session) {
        try {
            MessageManager messageManager = mailboxManager.getMailbox(mailboxId, session);
            MessageId messageId = messageManager.appendMessage(MessageManager.AppendCommand
                    .builder()
                    .notRecent()
                    .withFlags(flags)
                    .build(message),
                session)
                .getMessageId();
            lastMessageIdUsed = Optional.of(messageId);
            return messageId;
        } catch (MailboxException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public MessageId createNotUsedMessageId() {
        return InMemoryMessageId.of(Long.valueOf(lastMessageIdUsed.orElse(FIRST_MESSAGE_ID).serialize()) + ONE_HUNDRED);
    }

    @Override
    public void deleteMailbox(MailboxId mailboxId, MailboxSession session) {
        try {
            Optional<MailboxMetaData> mailbox = retrieveMailbox(mailboxId, session);
            if (mailbox.isPresent()) {
                mailboxManager.deleteMailbox(mailbox.get().getPath(), session);
            }
        } catch (MailboxException e) {
            Throwables.propagate(e);
        }
    }

    private Optional<MailboxMetaData> retrieveMailbox(MailboxId mailboxId, MailboxSession mailboxSession) throws MailboxException {
        MailboxQuery userMailboxesQuery = MailboxQuery.privateMailboxesBuilder(mailboxSession)
            .matchesAllMailboxNames()
            .build();
        return mailboxManager.search(userMailboxesQuery, mailboxSession)
            .stream()
            .filter(mailboxMetaData -> mailboxMetaData.getId().equals(mailboxId))
            .findFirst();
    }

    @Override
    public int getConstantMessageSize() {
        try {
            return DefaultMessageWriter.asBytes(message).length;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void setACL(MailboxId mailboxId, MailboxACL mailboxAcl, MailboxSession session) throws MailboxException {
        mailboxManager.setRights(mailboxId, mailboxAcl, session);
    }
}
