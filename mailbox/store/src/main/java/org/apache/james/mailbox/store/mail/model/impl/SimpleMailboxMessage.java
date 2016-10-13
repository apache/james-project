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
import java.util.List;

import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.DelegatingMailboxMessage;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

public class SimpleMailboxMessage extends DelegatingMailboxMessage {

    public static SimpleMailboxMessage copy(MailboxId mailboxId, MailboxMessage original) throws MailboxException {
        return copy(mailboxId, original, original.getAttachments());
    }

    public static SimpleMailboxMessage cloneWithAttachments(MailboxMessage mailboxMessage, List<MessageAttachment> attachments) throws MailboxException {
        SimpleMailboxMessage simpleMailboxMessage = copy(mailboxMessage.getMailboxId(), mailboxMessage, attachments);
        simpleMailboxMessage.setUid(mailboxMessage.getUid());
        simpleMailboxMessage.setModSeq(mailboxMessage.getModSeq());
        return simpleMailboxMessage;
    }

    private static SimpleMailboxMessage copy(MailboxId mailboxId, MailboxMessage original, List<MessageAttachment> attachments) throws MailboxException {
        Date internalDate = original.getInternalDate();
        long size = original.getFullContentOctets();
        Flags flags = original.createFlags();
        SharedByteArrayInputStream content = copyFullContent(original);
        int bodyStartOctet = Ints.checkedCast(original.getFullContentOctets() - original.getBodyOctets());
        PropertyBuilder pBuilder = new PropertyBuilder(original.getProperties());
        pBuilder.setTextualLineCount(original.getTextualLineCount());
        return new SimpleMailboxMessage(original.getMessageId(), internalDate, size, bodyStartOctet, content, flags, pBuilder, mailboxId, attachments);
    }

    private static SharedByteArrayInputStream copyFullContent(MailboxMessage original) throws MailboxException {
        try {
            return new SharedByteArrayInputStream(IOUtils.toByteArray(original.getFullContent()));
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    private MessageUid uid;
    private final MailboxId mailboxId;
    private boolean answered;
    private boolean deleted;
    private boolean draft;
    private boolean flagged;
    private boolean recent;
    private boolean seen;
    private String[] userFlags;
    private long modSeq;

    public SimpleMailboxMessage(MessageId messageId, Date internalDate, long size, int bodyStartOctet,
            SharedInputStream content, Flags flags,
            PropertyBuilder propertyBuilder, MailboxId mailboxId, List<MessageAttachment> attachments) {
        super(new SimpleMessage(
                messageId,
                content, size, internalDate, propertyBuilder.getSubType(),
                propertyBuilder.getMediaType(),
                bodyStartOctet,
                propertyBuilder.getTextualLineCount(),
                propertyBuilder.toProperties(),
                attachments
                ));

            setFlags(flags);
            this.mailboxId = mailboxId;
            this.userFlags = flags.getUserFlags();
    }

    public SimpleMailboxMessage(MessageId messageId, Date internalDate, long size, int bodyStartOctet,
                                SharedInputStream content, Flags flags,
                                PropertyBuilder propertyBuilder, MailboxId mailboxId) {
        this(messageId, internalDate, size, bodyStartOctet,
                content, flags,
                propertyBuilder, mailboxId, ImmutableList.<MessageAttachment>of());
    }

    @Override
    protected String[] createUserFlags() {
        return userFlags.clone();
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    public MessageUid getUid() {
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

    public void setUid(MessageUid uid) {
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
        return Objects.hashCode(uid);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleMailboxMessage) {
            SimpleMailboxMessage other = (SimpleMailboxMessage) obj;
            return Objects.equal(this.uid, other.uid);
        }
        return false;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
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
