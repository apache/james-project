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

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MessageUtils {
    private final MailboxSession mailboxSession;
    private final UidProvider uidProvider;
    private final ModSeqProvider modSeqProvider;

    public MessageUtils(MailboxSession mailboxSession, UidProvider uidProvider, ModSeqProvider modSeqProvider) {
        Preconditions.checkNotNull(uidProvider);
        Preconditions.checkNotNull(modSeqProvider);
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
        return modSeqProvider.nextModSeq(mailboxSession, mailbox);
    }

    public void enrichMessage(Mailbox mailbox, MailboxMessage message) throws MailboxException { 
        message.setUid(nextUid(mailbox));
        message.setModSeq(nextModSeq(mailbox));
    }

    public MessageChangedFlags updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, 
            Iterator<MailboxMessage> messages) throws MailboxException {
        ImmutableList.Builder<UpdatedFlags> updatedFlags = ImmutableList.builder();
        ImmutableList.Builder<MailboxMessage> changedFlags = ImmutableList.builder();

        long modSeq = nextModSeq(mailbox);

        while (messages.hasNext()) {
            MailboxMessage member = messages.next();
            Flags originalFlags = member.createFlags();
            member.setFlags(flagsUpdateCalculator.buildNewFlags(originalFlags));
            Flags newFlags = member.createFlags();
            if (UpdatedFlags.flagsChanged(originalFlags, newFlags)) {
                member.setModSeq(modSeq);
                changedFlags.add(member);
            }

            updatedFlags.add(UpdatedFlags.builder()
                .uid(member.getUid())
                .modSeq(member.getModSeq())
                .newFlags(newFlags)
                .oldFlags(originalFlags)
                .build());
        }

        return new MessageChangedFlags(updatedFlags.build().iterator(), changedFlags.build());
    }

    
    public class MessageChangedFlags {
        private final Iterator<UpdatedFlags> updatedFlags;
        private final List<MailboxMessage> changedFlags;

        public MessageChangedFlags(Iterator<UpdatedFlags> updatedFlags, List<MailboxMessage> changedFlags) {
            this.updatedFlags = updatedFlags;
            this.changedFlags = changedFlags;
        }

        public Iterator<UpdatedFlags> getUpdatedFlags() {
            return updatedFlags;
        }

        public List<MailboxMessage> getChangedFlags() {
            return changedFlags;
        }
    }
}
