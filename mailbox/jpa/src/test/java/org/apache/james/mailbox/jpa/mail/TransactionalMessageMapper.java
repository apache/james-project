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
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.transaction.Mapper;

public class TransactionalMessageMapper implements MessageMapper {
    private final JPAMessageMapper messageMapper;

    public TransactionalMessageMapper(JPAMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }
    @Override
    public void endRequest() {
        throw new NotImplementedException();
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
        return MailboxCounters.builder()
            .count(countMessagesInMailbox(mailbox))
            .unseen(countUnseenMessagesInMailbox(mailbox))
            .build();
    }

    @Override
    public Iterator<MessageUid> listAllMessageUids(Mailbox mailbox) throws MailboxException {
        return messageMapper.listAllMessageUids(mailbox);
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType type, int limit)
            throws MailboxException {
        return messageMapper.findInMailbox(mailbox, set, type, limit);
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(final Mailbox mailbox, final MessageRange set)
            throws MailboxException {
        Map<MessageUid, MessageMetaData> data = messageMapper.execute(
            () -> messageMapper.expungeMarkedForDeletionInMailbox(mailbox, set));
        return data;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return messageMapper.countMessagesInMailbox(mailbox);
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return messageMapper.countUnseenMessagesInMailbox(mailbox);
    }

    @Override
    public void delete(final Mailbox mailbox, final MailboxMessage message) throws MailboxException {
        messageMapper.execute(Mapper.toTransaction(() -> messageMapper.delete(mailbox, message)));
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        return messageMapper.findFirstUnseenMessageUid(mailbox);
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        return messageMapper.findRecentMessageUidsInMailbox(mailbox);
    }

    @Override
    public MessageMetaData add(final Mailbox mailbox, final MailboxMessage message) throws MailboxException {
        MessageMetaData data = messageMapper.execute(
            () -> messageMapper.add(mailbox, message));
        return data;
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(final Mailbox mailbox, final FlagsUpdateCalculator flagsUpdateCalculator,
            final MessageRange set) throws MailboxException {
        Iterator<UpdatedFlags> data = messageMapper.execute(
            () -> messageMapper.updateFlags(mailbox, flagsUpdateCalculator, set));
        return data;
    }

    @Override
    public MessageMetaData copy(final Mailbox mailbox, final MailboxMessage original) throws MailboxException {
        MessageMetaData data = messageMapper.execute(
            () -> messageMapper.copy(mailbox, original));
        return data;
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        return messageMapper.move(mailbox, original);
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return messageMapper.getLastUid(mailbox);
    }

    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return messageMapper.getHighestModSeq(mailbox);
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        return messageMapper.getApplicableFlag(mailbox);
    }
}
