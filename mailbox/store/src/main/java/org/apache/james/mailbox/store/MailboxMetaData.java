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

import javax.mail.Flags;

import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxACL;

/**
 * Describes the current state of a mailbox.
 */
public class MailboxMetaData implements MessageManager.MetaData {

    private final long recentCount;
    private final List<Long> recent;
    private final Flags premanentFlags;
    private final long uidValidity;
    private final long nextUid;
    private final long messageCount;
    private final long unseenCount;
    private final Long firstUnseen;
    private final boolean writeable;
    private final long highestModSeq;
    private final boolean modSeqPermanent;
    private final MailboxACL acl;

    public MailboxMetaData(final List<Long> recent, final Flags premanentFlags, final long uidValidity, final long nextUid, final long highestModSeq, final long messageCount, final long unseenCount, final Long firstUnseen, final boolean writeable, final boolean modSeqPermanent, MailboxACL acl) {
        super();
        if (recent == null) {
            this.recent = new ArrayList<Long>();
        } else {
            this.recent = recent;

        }
        this.highestModSeq = highestModSeq;
        recentCount = this.recent.size();

        this.premanentFlags = premanentFlags;
        this.uidValidity = uidValidity;
        this.nextUid = nextUid;
        this.messageCount = messageCount;
        this.unseenCount = unseenCount;
        this.firstUnseen = firstUnseen;
        this.writeable = writeable;
        this.modSeqPermanent = modSeqPermanent;
        this.acl = acl;
    }

    /**
     * @see MailboxMetaData#countRecent()
     */
    public long countRecent() {
        return recentCount;
    }

    /**
     * @see MailboxMetaData#getPermanentFlags()
     */
    public Flags getPermanentFlags() {
        return premanentFlags;
    }

    /**
     * @see MailboxMetaData#getRecent()
     */
    public List<Long> getRecent() {
        return recent;
    }

    /**
     * @see MailboxMetaData#getUidValidity()
     */
    public long getUidValidity() {
        return uidValidity;
    }

    /**
     * @see MailboxMetaData#getUidNext()
     */
    public long getUidNext() {
        return nextUid;
    }

    /**
     * @see MailboxMetaData#getMessageCount()
     */
    public long getMessageCount() {
        return messageCount;
    }

    /**
     * @see MailboxMetaData#getUnseenCount()
     */
    public long getUnseenCount() {
        return unseenCount;
    }

    /**
     * @see MailboxMetaData#getFirstUnseen()
     */
    public Long getFirstUnseen() {
        return firstUnseen;
    }

    /**
     * @see MailboxMetaData#isWriteable()
     */
    public boolean isWriteable() {
        return writeable;
    }

    /**
     * @see MailboxMetaData#getHighestModSeq()
     */
    public long getHighestModSeq() {
        return highestModSeq;
    }

    /**
     * @see MailboxMetaData#isModSeqPermanent()
     */
    public boolean isModSeqPermanent() {
        return modSeqPermanent;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.MessageManager.MetaData#getACL()
     */
    @Override
    public MailboxACL getACL() {
        return acl;
    }
}
