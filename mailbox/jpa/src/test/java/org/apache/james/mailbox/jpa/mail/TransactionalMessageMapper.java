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

package org.apache.james.mailbox.jpa.mail;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

public class TransactionalMessageMapper implements MessageMapper {
    private final JPAMessageMapper wrapped;

    public TransactionalMessageMapper(JPAMessageMapper wrapped) {
        this.wrapped = wrapped;
    }
    @Override
    public void endRequest() {
        throw new NotImplementedException();
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType type, int limit)
            throws MailboxException {
        return wrapped.findInMailbox(mailbox, set, type, limit);
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(final Mailbox mailbox, final MessageRange set)
            throws MailboxException {
        Map<MessageUid, MessageMetaData> data = wrapped.execute(new Transaction<Map<MessageUid, MessageMetaData>>() {
            @Override
            public Map<MessageUid, MessageMetaData> run() throws MailboxException {
                return wrapped.expungeMarkedForDeletionInMailbox(mailbox, set);
            }
        });
        return data;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return wrapped.countMessagesInMailbox(mailbox);
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return wrapped.countUnseenMessagesInMailbox(mailbox);
    }

    @Override
    public void delete(final Mailbox mailbox, final MailboxMessage message) throws MailboxException {
        try {
            wrapped.execute(new VoidTransaction() {
                @Override
                public void runVoid() throws MailboxException {
                    wrapped.delete(mailbox, message);
                }
            });
        } catch (MailboxException e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        return wrapped.findFirstUnseenMessageUid(mailbox);
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        return wrapped.findRecentMessageUidsInMailbox(mailbox);
    }

    @Override
    public MessageMetaData add(final Mailbox mailbox, final MailboxMessage message) throws MailboxException {
        MessageMetaData data = wrapped.execute(new Transaction<MessageMetaData>() {
            @Override
            public MessageMetaData run() throws MailboxException {
                return wrapped.add(mailbox, message);
            }
        });
        return data;
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(final Mailbox mailbox, final FlagsUpdateCalculator flagsUpdateCalculator,
            final MessageRange set) throws MailboxException {
        Iterator<UpdatedFlags> data = wrapped.execute(new Transaction<Iterator<UpdatedFlags>>() {
            @Override
            public Iterator<UpdatedFlags> run() throws MailboxException {
                return wrapped.updateFlags(mailbox, flagsUpdateCalculator, set);
            }
        });
        return data;
    }

    @Override
    public MessageMetaData copy(final Mailbox mailbox, final MailboxMessage original) throws MailboxException {
        MessageMetaData data = wrapped.execute(new Transaction<MessageMetaData>() {
            @Override
            public MessageMetaData run() throws MailboxException {
                return wrapped.copy(mailbox, original);
            }
        });
        return data;
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        return wrapped.move(mailbox, original);
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return wrapped.getLastUid(mailbox);
    }

    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return wrapped.getHighestModSeq(mailbox);
    }

}
