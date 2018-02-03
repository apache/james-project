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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;

import com.google.common.collect.ImmutableList;

/**
 * Describes the current state of a mailbox.
 */
public class MailboxMetaData implements MessageManager.MetaData {

    public static MailboxMetaData sensibleInformationFree(MailboxACL resolvedAcl, long uidValidity, boolean writeable, boolean modSeqPermanent) throws MailboxException {
        ImmutableList<MessageUid> recents = ImmutableList.of();
        MessageUid uidNext = MessageUid.MIN_VALUE;
        long highestModSeq = 0L;
        long messageCount = 0L;
        long unseenCount = 0L;
        MessageUid firstUnseen = null;
        return new MailboxMetaData(
            recents,
            new Flags(),
            uidValidity,
            uidNext,
            highestModSeq,
            messageCount,
            unseenCount,
            firstUnseen,
            writeable,
            modSeqPermanent,
            resolvedAcl);
    }

    private final long recentCount;
    private final List<MessageUid> recent;
    private final Flags permanentFlags;
    private final long uidValidity;
    private final MessageUid nextUid;
    private final long messageCount;
    private final long unseenCount;
    private final MessageUid firstUnseen;
    private final boolean writeable;
    private final long highestModSeq;
    private final boolean modSeqPermanent;
    private final MailboxACL acl;

    public MailboxMetaData(List<MessageUid> recent, Flags permanentFlags, long uidValidity, MessageUid uidNext, long highestModSeq, long messageCount, long unseenCount, MessageUid firstUnseen, boolean writeable, boolean modSeqPermanent, MailboxACL acl) {
        super();
        this.recent = Optional.ofNullable(recent).orElseGet(ArrayList::new);
        this.highestModSeq = highestModSeq;
        this.recentCount = this.recent.size();

        this.permanentFlags = permanentFlags;
        this.uidValidity = uidValidity;
        this.nextUid = uidNext;
        this.messageCount = messageCount;
        this.unseenCount = unseenCount;
        this.firstUnseen = firstUnseen;
        this.writeable = writeable;
        this.modSeqPermanent = modSeqPermanent;
        this.acl = acl;
    }

    @Override
    public long countRecent() {
        return recentCount;
    }

    @Override
    public Flags getPermanentFlags() {
        return permanentFlags;
    }

    @Override
    public List<MessageUid> getRecent() {
        return recent;
    }

    @Override
    public long getUidValidity() {
        return uidValidity;
    }

    @Override
    public MessageUid getUidNext() {
        return nextUid;
    }

    @Override
    public long getMessageCount() {
        return messageCount;
    }

    @Override
    public long getUnseenCount() {
        return unseenCount;
    }

    @Override
    public MessageUid getFirstUnseen() {
        return firstUnseen;
    }

    @Override
    public boolean isWriteable() {
        return writeable;
    }

    @Override
    public long getHighestModSeq() {
        return highestModSeq;
    }

    @Override
    public boolean isModSeqPermanent() {
        return modSeqPermanent;
    }

    @Override
    public MailboxACL getACL() {
        return acl;
    }
}
