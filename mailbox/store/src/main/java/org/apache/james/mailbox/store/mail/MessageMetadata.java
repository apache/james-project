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

package org.apache.james.mailbox.store.mail;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Optional;

public class MessageMetadata {
    private final MailboxSession mailboxSession;
    private final UidProvider uidProvider;
    private final ModSeqProvider modSeqProvider;

    public MessageMetadata(MailboxSession mailboxSession, UidProvider uidProvider, ModSeqProvider modSeqProvider) {
        this.mailboxSession = mailboxSession;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
    }
    
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailboxSession, mailbox);
    }

    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailboxSession, mailbox);
    }
    

    public MessageUid nextUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.nextUid(mailboxSession, mailbox);
    }

    public long nextModSeq(Mailbox mailbox) throws MailboxException {
        if (modSeqProvider != null) {
            return modSeqProvider.nextModSeq(mailboxSession, mailbox);
        }
        return -1;
    }

    public void enrichMessage(Mailbox mailbox, MailboxMessage message) throws MailboxException { 
        message.setUid(nextUid(mailbox));
        
        if (modSeqProvider != null) {
            message.setModSeq(nextModSeq(mailbox));
        }
    }

}
