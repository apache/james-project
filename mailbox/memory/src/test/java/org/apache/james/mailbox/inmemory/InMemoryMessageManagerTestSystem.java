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

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.MessageManagerTestSystem;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;

public class InMemoryMessageManagerTestSystem extends MessageManagerTestSystem {

    private static final MessageId FIRST_MESSAGE_ID = InMemoryMessageId.of(1);
    private static final long ONE_HUNDRED = 100;
    private static final int UID_VALIDITY = 1024;
    public static final byte[] CONTENT = "Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8);

    private final MailboxManager mailboxManager;
    private Optional<MessageId> lastMessageIdUsed;

    public InMemoryMessageManagerTestSystem(MailboxManager mailboxManager) throws MailboxException {
        super(mailboxManager);
        this.mailboxManager = mailboxManager;
        this.lastMessageIdUsed = Optional.empty();
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
            MessageId messageId = messageManager.appendMessage(new ByteArrayInputStream(CONTENT), new Date(), session, false, flags)
                    .getMessageId();
            lastMessageIdUsed = Optional.of(messageId);
            return messageId;
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public MessageId createNotUsedMessageId() {
        return InMemoryMessageId.of(Long.valueOf(lastMessageIdUsed.orElse(FIRST_MESSAGE_ID).serialize()) + ONE_HUNDRED);
    }

    @Override
    public void deleteMailbox(final MailboxId mailboxId, MailboxSession session) {
        try {
            Optional<MailboxMetaData> mailbox = retrieveMailbox(mailboxId, session);
            if (mailbox.isPresent()) {
                mailboxManager.deleteMailbox(mailbox.get().getPath(), session);
            }
        } catch (MailboxException e) {
            Throwables.propagate(e);
        }
    }

    private Optional<MailboxMetaData> retrieveMailbox(final MailboxId mailboxId, MailboxSession mailboxSession) throws MailboxException {
        MailboxQuery userMailboxesQuery = MailboxQuery.builder(mailboxSession).expression("*").build();
        return mailboxManager.search(userMailboxesQuery, mailboxSession)
            .stream()
            .filter(mailboxMetaData -> mailboxMetaData.getId().equals(mailboxId))
            .findFirst();
    }

    @Override
    public int getConstantMessageSize() {
        return CONTENT.length;
    }
}
