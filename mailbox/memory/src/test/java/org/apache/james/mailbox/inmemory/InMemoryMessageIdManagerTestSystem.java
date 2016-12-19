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

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;

public class InMemoryMessageIdManagerTestSystem extends MessageIdManagerTestSystem {

    private static final MessageId FIRST_MESSAGE_ID = InMemoryMessageId.of(1);
    private static final long ONE_HUNDRED = 100;

    private final MailboxManager mailboxManager;
    private final MailboxSession mailboxSession;
    private Optional<MessageId> lastMessageIdUsed;

    public InMemoryMessageIdManagerTestSystem(MailboxManager mailboxManager, MailboxSession mailboxSession, 
            Mailbox mailbox1, Mailbox mailbox2, Mailbox mailbox3, Mailbox mailbox4) {
        super(new InMemoryMessageIdManager(mailboxManager), mailboxSession, mailbox1, mailbox2, mailbox3, mailbox4);
        this.mailboxManager = mailboxManager;
        this.mailboxSession = mailboxSession;
        this.lastMessageIdUsed = Optional.absent();
    }

    @Override
    public MessageId persist(MailboxId mailboxId, MessageUid uid, Flags flags) {
        try {
            MessageManager messageManager = mailboxManager.getMailbox(mailboxId, mailboxSession);
            MessageId messageId = messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, flags)
                    .getMessageId();
            lastMessageIdUsed = Optional.of(messageId);
            return messageId;
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public MessageId createNotUsedMessageId() {
        return InMemoryMessageId.of(Long.valueOf(lastMessageIdUsed.or(FIRST_MESSAGE_ID).serialize()) + ONE_HUNDRED);
    }

    @Override
    public void deleteMailbox(final MailboxId mailboxId) {
        try {
            Optional<MailboxMetaData> mailbox = retrieveMailbox(mailboxId);
            if (mailbox.isPresent()) {
                mailboxManager.deleteMailbox(mailbox.get().getPath(), mailboxSession);
            }
        } catch (MailboxException e) {
            Throwables.propagate(e);
        }
    }

    private Optional<MailboxMetaData> retrieveMailbox(final MailboxId mailboxId) throws MailboxException {
        MailboxQuery userMailboxesQuery = MailboxQuery.builder(mailboxSession).expression("*").build();
        return FluentIterable.from(mailboxManager.search(userMailboxesQuery, mailboxSession))
            .filter(new Predicate<MailboxMetaData>() {

                @Override
                public boolean apply(MailboxMetaData mailboxMetaData) {
                    return mailboxMetaData.getId().equals(mailboxId);
                }
            })
            .first();
    }

    @Override
    public void clean() {

    }

}
