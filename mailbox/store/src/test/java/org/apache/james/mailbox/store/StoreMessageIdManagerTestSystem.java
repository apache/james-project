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

package org.apache.james.mailbox.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.mail.Flags;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.SessionType;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Throwables;

public class StoreMessageIdManagerTestSystem extends MessageIdManagerTestSystem {
    private static final MailboxSession EMPTY_MAILBOX_SESSION = new MockMailboxSession("user", SessionType.System);
    private static final long MOD_SEQ = 18;
    private static final ByteArrayInputStream ARRAY_INPUT_STREAM = new ByteArrayInputStream("".getBytes());

    private final MessageId.Factory messageIdFactory;
    private final TestMailboxSessionMapperFactory mapperFactory;

    public StoreMessageIdManagerTestSystem(MessageIdManager messageIdManager, Mailbox mailbox1, Mailbox mailbox2, Mailbox mailbox3, Mailbox mailbox4,
                                           MessageId.Factory messageIdFactory, TestMailboxSessionMapperFactory mapperFactory) {
        super(messageIdManager, EMPTY_MAILBOX_SESSION, mailbox1, mailbox2, mailbox3, mailbox4);

        this.messageIdFactory = messageIdFactory;
        this.mapperFactory = mapperFactory;
    }

    @Override
    public MessageId persist(MailboxId mailboxId, MessageUid uid, Flags flags) {
        MessageId messageId = messageIdFactory.generate();
        try {
            mapperFactory.createMessageIdMapper(EMPTY_MAILBOX_SESSION)
                .save(createMessage(mailboxId, flags, messageId, uid));
            return messageId;
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void deleteMailbox(MailboxId mailboxId) {
        throw new NotImplementedException();
    }

    @Override
    public MessageId createNotUsedMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    public void clean() {
        mapperFactory.clean();
    }

    private MailboxMessage createMessage(MailboxId mailboxId, Flags flags, MessageId messageId, MessageUid uid) {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        when(mailboxMessage.getMessageId()).thenReturn(messageId);
        when(mailboxMessage.getUid()).thenReturn(uid);
        when(mailboxMessage.getModSeq()).thenReturn(MOD_SEQ);
        when(mailboxMessage.getMailboxId()).thenReturn(mailboxId);
        try {
            when(mailboxMessage.getFullContent()).thenReturn(ARRAY_INPUT_STREAM);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        when(mailboxMessage.createFlags()).thenReturn(flags);
        return mailboxMessage;
    }
}
