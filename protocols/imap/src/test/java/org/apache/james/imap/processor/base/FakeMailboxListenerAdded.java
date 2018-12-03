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

package org.apache.james.imap.processor.base;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;

public class FakeMailboxListenerAdded extends MailboxListener.Added {

    public List<MessageUid> uids;

    public FakeMailboxListenerAdded(MailboxSession.SessionId sessionId, User user, List<MessageUid> uids, MailboxPath path, MailboxId mailboxId) {
        super(Optional.ofNullable(sessionId), user, path, mailboxId);
        this.uids = uids;
    }

    @Override
    public List<MessageUid> getUids() {
        return uids;
    }

    @Override
    public MessageMetaData getMetaData(MessageUid uid) {
        return new MessageMetaData() {
            
            @Override
            public MessageUid getUid() {
                return null;
            }
            
            @Override
            public long getSize() {
                return 0;
            }
            
            @Override
            public Date getInternalDate() {
                return null;
            }
            
            @Override
            public Flags getFlags() {
                return null;
            }

            @Override
            public long getModSeq() {
                return 0;
            }
            
            @Override
            public MessageId getMessageId() {
                return null;
            }
        };
    }


}
