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

package org.apache.james.mailbox.store.mail.model.impl;

import java.io.IOException;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.DelegatingMailboxMessage;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;

public class SimpleMailboxMessage<Id extends MailboxId> extends DelegatingMailboxMessage<Id> {

    public static <Id extends MailboxId> SimpleMailboxMessage<Id> copy(Id mailboxId, MailboxMessage<Id> original) throws MailboxException {
        Date internalDate = original.getInternalDate();
        long size = original.getFullContentOctets();
        Flags flags = original.createFlags();
        SharedByteArrayInputStream content = copyFullContent(original);
        int bodyStartOctet = Ints.checkedCast(original.getFullContentOctets() - original.getBodyOctets());
        PropertyBuilder pBuilder = new PropertyBuilder(original.getProperties());
        pBuilder.setTextualLineCount(original.getTextualLineCount());
        return new SimpleMailboxMessage<Id>(internalDate, size, bodyStartOctet, content, flags, pBuilder, mailboxId);
    }

    private static <Id extends MailboxId> SharedByteArrayInputStream copyFullContent(MailboxMessage<Id> original) throws MailboxException {
        try {
            return new SharedByteArrayInputStream(IOUtils.toByteArray(original.getFullContent()));
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    private long uid;
    private final Id mailboxId;
    private boolean answered;
    private boolean deleted;
    private boolean draft;
    private boolean flagged;
    private boolean recent;
    private boolean seen;
    private String[] userFlags;
    private long modSeq;

    public SimpleMailboxMessage(Date internalDate, long size, int bodyStartOctet,
                                SharedInputStream content, Flags flags,
                                PropertyBuilder propertyBuilder, Id mailboxId) {
        super(new SimpleMessage(
            content, size, internalDate, propertyBuilder.getSubType(),
            propertyBuilder.getMediaType(),
            bodyStartOctet,
            propertyBuilder.getTextualLineCount(),
            propertyBuilder.toProperties()
            ));

        setFlags(flags);
        this.mailboxId = mailboxId;
        this.userFlags = flags.getUserFlags();
    }

    @Override
    protected String[] createUserFlags() {
        return userFlags.clone();
    }

    public Id getMailboxId() {
        return mailboxId;
    }

    public long getUid() {
        return uid;
    }

    public boolean isAnswered() {
        return answered;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isDraft() {
        return draft;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public boolean isRecent() {
        return recent;
    }

    public boolean isSeen() {
        return seen;
    }

    public long getModSeq() {
        return modSeq;
    }

    public void setModSeq(long modSeq) {
        this.modSeq = modSeq;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public synchronized void setFlags(Flags flags) {
        answered = flags.contains(Flags.Flag.ANSWERED);
        deleted = flags.contains(Flags.Flag.DELETED);
        draft = flags.contains(Flags.Flag.DRAFT);
        flagged = flags.contains(Flags.Flag.FLAGGED);
        recent = flags.contains(Flags.Flag.RECENT);
        seen = flags.contains(Flags.Flag.SEEN);
        userFlags = flags.getUserFlags();
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (uid ^ (uid >>> 32));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final SimpleMailboxMessage<Id> other = (SimpleMailboxMessage<Id>) obj;
        if (uid != other.uid)
            return false;
        return true;
    }

    public String toString() {
        return Objects.toStringHelper(this)
            .add("uid", this.uid)
            .add("mailboxId", this.mailboxId)
            .add("answered", this.answered)
            .add("deleted", this.deleted)
            .add("draft", this.draft)
            .add("flagged", this.flagged)
            .add("recent", this.recent)
            .add("seen", this.seen)
            .add("message", this.getMessage())
            .toString();
    }

}
