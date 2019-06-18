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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.transaction.TransactionalMapper;

import com.google.common.collect.Iterators;

/**
 * Abstract base class for {@link MessageMapper} implementation
 * which already takes care of most uid / mod-seq handling.
 *
 */
public abstract class AbstractMessageMapper extends TransactionalMapper implements MessageMapper {

    public static final int UNLIMITED = -1;

    protected final MailboxSession mailboxSession;
    private final UidProvider uidProvider;
    private final ModSeqProvider modSeqProvider;

    public AbstractMessageMapper(MailboxSession mailboxSession, UidProvider uidProvider, ModSeqProvider modSeqProvider) {
        this.mailboxSession = mailboxSession;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
    }
    
    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailboxSession, mailbox);
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailboxSession, mailbox);
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
        return MailboxCounters.builder()
            .count(countMessagesInMailbox(mailbox))
            .unseen(countUnseenMessagesInMailbox(mailbox))
            .build();
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange set) throws MailboxException {
        final List<UpdatedFlags> updatedFlags = new ArrayList<>();
        Iterator<MailboxMessage> messages = findInMailbox(mailbox, set, FetchType.Metadata, UNLIMITED);
        
        long modSeq = -1;
        if (messages.hasNext()) {
            // if a mailbox does not support mod-sequences the provider may be null
            if (modSeqProvider != null) {
                modSeq = modSeqProvider.nextModSeq(mailboxSession, mailbox);
            }
        }
        while (messages.hasNext()) {
            final MailboxMessage member = messages.next();
            Flags originalFlags = member.createFlags();
            member.setFlags(flagsUpdateCalculator.buildNewFlags(originalFlags));
            Flags newFlags = member.createFlags();
            if (UpdatedFlags.flagsChanged(originalFlags, newFlags)) {
                // increase the mod-seq as we changed the flags
                member.setModSeq(modSeq);
                save(mailbox, member);
            }
            
            updatedFlags.add(UpdatedFlags.builder()
                .uid(member.getUid())
                .modSeq(member.getModSeq())
                .newFlags(newFlags)
                .oldFlags(originalFlags)
                .build());
            
        }

        return updatedFlags.iterator();

    }

    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        message.setUid(uidProvider.nextUid(mailboxSession, mailbox));
        
        // if a mailbox does not support mod-sequences the provider may be null
        if (modSeqProvider != null) {
            message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
        }
        MessageMetaData data = save(mailbox, message);
       
        return data;
        
    }

    
    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        MessageUid uid = uidProvider.nextUid(mailboxSession, mailbox);
        long modSeq = -1;
        if (modSeqProvider != null) {
            modSeq = modSeqProvider.nextModSeq(mailboxSession, mailbox);
        }
        final MessageMetaData metaData = copy(mailbox, uid, modSeq, original);  
        
        return metaData;
    }

    /**
     * Save the {@link MailboxMessage} for the given {@link Mailbox} and return the {@link MessageMetaData}
     */
    protected abstract MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException;

    
    /**
     * Copy the MailboxMessage to the Mailbox, using the given uid and modSeq for the new MailboxMessage
     */
    protected abstract MessageMetaData copy(Mailbox mailbox, MessageUid uid, long modSeq, MailboxMessage original) throws MailboxException;

    @Override
    public Iterator<MessageUid> listAllMessageUids(Mailbox mailbox) throws MailboxException {
        return Iterators.transform(findInMailbox(mailbox, MessageRange.all(), FetchType.Metadata, UNLIMITED),
            MailboxMessage::getUid);
    }
}
