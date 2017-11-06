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

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public abstract class MessageIdManagerTestSystem {

    private final MessageIdManager messageIdManager;

    public MessageIdManagerTestSystem(MessageIdManager messageIdManager) {
        this.messageIdManager = messageIdManager;
    }

    public MessageIdManager getMessageIdManager() {
        return messageIdManager;
    }

    public abstract Mailbox createMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

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
    public abstract MessageId persist(MailboxId mailboxId, MessageUid uid, Flags flags, MailboxSession session);

    public abstract MessageId createNotUsedMessageId();

    public abstract void deleteMailbox(MailboxId mailboxId, MailboxSession session);

    public abstract int getConstantMessageSize();

    public abstract void setACL(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException;
}
