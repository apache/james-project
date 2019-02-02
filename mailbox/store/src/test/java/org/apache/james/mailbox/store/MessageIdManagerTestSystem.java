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

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

public class MessageIdManagerTestSystem {
    private static final byte[] MESSAGE_CONTENT = "subject: any\n\nbody".getBytes(StandardCharsets.UTF_8);
    public static final int MOD_SEQ = 452;

    private final MessageIdManager messageIdManager;
    private final MessageId.Factory messageIdFactory;
    private final MailboxSessionMapperFactory mapperFactory;
    private final StoreMailboxManager mailboxManager;

    /**
     * Should take care of find returning the MailboxMessage
     * Should take care of findMailboxes returning the mailbox the message is in
     * Should persist flags 
     * Should keep track of flag state for setFlags
     * 
     * @param mailboxId
     * @param flags
     * @return the id of persisted message
     */

    public MessageIdManagerTestSystem(MessageIdManager messageIdManager, MessageId.Factory messageIdFactory, MailboxSessionMapperFactory mapperFactory, StoreMailboxManager mailboxManager) {
        this.messageIdManager = messageIdManager;
        this.messageIdFactory = messageIdFactory;
        this.mapperFactory = mapperFactory;
        this.mailboxManager = mailboxManager;
    }


    public MessageIdManager getMessageIdManager() {
        return messageIdManager;
    }

    public Mailbox createMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        mailboxManager.createMailbox(mailboxPath, session);
        return mapperFactory.getMailboxMapper(session).findMailboxByPath(mailboxPath);
    }

    public MessageId persist(MailboxId mailboxId, MessageUid uid, Flags flags, MailboxSession mailboxSession) {
        try {
            MessageId messageId = messageIdFactory.generate();
            Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
            MailboxMessage message = createMessage(mailboxId, flags, messageId, uid);
            mapperFactory.getMessageMapper(mailboxSession).add(mailbox, message);
            mailboxManager.getEventBus().dispatch(EventFactory.added()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(mailbox)
                .addMessage(message)
                .build(),
                new MailboxIdRegistrationKey(mailboxId))
            .block();
            return messageId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MessageId createNotUsedMessageId() {
        return messageIdFactory.generate();
    }

    public void deleteMailbox(MailboxId mailboxId, MailboxSession mailboxSession) {
        try {
            Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
            mailboxManager.deleteMailbox(new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName()), mailboxSession);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static MailboxMessage createMessage(MailboxId mailboxId, Flags flags, MessageId messageId, MessageUid uid) {
        MailboxMessage mailboxMessage = new SimpleMailboxMessage(messageId, new Date(), MESSAGE_CONTENT.length, 1256,
            new SharedByteArrayInputStream(MESSAGE_CONTENT), flags, new PropertyBuilder(), mailboxId);
        mailboxMessage.setModSeq(MOD_SEQ);
        mailboxMessage.setUid(uid);
        return mailboxMessage;
    }

    public int getConstantMessageSize() {
        return MESSAGE_CONTENT.length;
    }

    public void setACL(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        mailboxManager.setRights(mailboxId, mailboxACL, session);
    }
}
