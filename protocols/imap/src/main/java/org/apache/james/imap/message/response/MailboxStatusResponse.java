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

package org.apache.james.imap.message.response;

import java.util.Optional;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.UidValidity;

/**
 * Represents a <code>STATUS</code> response. See <code>RFC3501 7.2.4</code>.
 */
public class MailboxStatusResponse implements ImapResponseMessage {
    private final Optional<Long> appendLimit;
    private final Long size;
    private final Long deleted;
    private final Long deletedStorage;
    private final Long messages;
    private final Long recent;
    private final MessageUid uidNext;
    private final UidValidity uidValidity;
    private final Long unseen;
    private final String mailbox;
    private final ModSeq highestModSeq;
    private final MailboxId mailboxId;

    public MailboxStatusResponse(Optional<Long> appendLimit, Long size, Long deleted, Long deletedStorage, Long messages, Long recent, MessageUid uidNext, ModSeq highestModSeq, UidValidity uidValidity, Long unseen, String mailbox, MailboxId mailboxId) {
        this.appendLimit = appendLimit;
        this.size = size;
        this.deleted = deleted;
        this.deletedStorage = deletedStorage;
        this.messages = messages;
        this.recent = recent;
        this.uidNext = uidNext;
        this.uidValidity = uidValidity;
        this.unseen = unseen;
        this.mailbox = mailbox;
        this.highestModSeq = highestModSeq;
        this.mailboxId = mailboxId;
    }

    public Optional<Long> getAppendLimit() {
        return appendLimit;
    }

    public Long getSize() {
        return size;
    }

    public Long getDeleted() {
        return deleted;
    }

    public Long getDeletedStorage() {
        return deletedStorage;
    }

    /**
     * Gets the <code>MESSAGES</code> count for the mailbox.
     * 
     * @return the message count for the mailbox (if requested) or null (if not)
     */
    public final Long getMessages() {
        return messages;
    }

    /**
     * Gets the <code>RECENT</code> count for the mailbox.
     * 
     * @return the recent count (if requested) or null (if not)
     */
    public final Long getRecent() {
        return recent;
    }

    /**
     * Gets the mailbox <code>UIDNEXT</code>.
     * 
     * @return the mailbox uidNext (if requested) or null (if not)
     */
    public final MessageUid getUidNext() {
        return uidNext;
    }

    /**
     * Gets the mailbox <code>UIDVALIDITY</code>.
     * 
     * @return the mailbox uidValidity (if requested) or null (if not)
     */
    public final UidValidity getUidValidity() {
        return uidValidity;
    }

    /**
     * Gets the <code>UNSEEN</code> count for the mailbox.
     * 
     * @return the unseen count (if requested) or null (if not)
     */
    public final Long getUnseen() {
        return unseen;
    }

    /**
     * Gets the mailbox name.
     * 
     * @return the mailbox name, not null
     */
    public final String getMailbox() {
        return mailbox;
    }
    
    /**
     * Gets the mailbox <code>HIGHESTMODSEQ</code>.
     * 
     * @return the mailbox highestModSeq (if requested) or null (if not)
     */
    public final ModSeq getHighestModSeq() {
        return highestModSeq;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    public String toString() {
        return "Status response[mailbox='" + mailbox + "' messages=" + messages + " recent=" + recent + " uidnext=" + uidNext + " uidvalidity=" + uidValidity + " unseen=" + unseen + "]";
    }
}
