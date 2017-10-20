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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public class SimpleMailboxMembership implements MailboxMessage {
    
    private static final String TOSTRING_SEPARATOR = " ";
    
    public TestId mailboxId;
    public MessageUid uid;
    public Date internalDate;
    public boolean recent = false;
    public boolean answered = false;
    public boolean deleted = false;
    public boolean draft = false;
    public boolean flagged = false;
    public boolean seen = false;

    private MessageId messageId;

    public SimpleMailboxMembership(MessageId messageId, TestId mailboxId, MessageUid uid, long modSeq, Date internalDate, int size, 
            Flags flags, byte[] body, Map<String, String> headers) throws Exception {
        super();
        this.messageId = messageId;
        this.mailboxId = mailboxId;
        this.uid = uid;
        this.internalDate = internalDate;
        this.size = size;
        this.body = body;
        if (headers == null) {
            this.headers = new HashMap<>();
        } else {
            this.headers = headers;
        }
        
        this.body =  body;
        setFlags(flags);
    }

    @Override
    public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
        return ComposedMessageIdWithMetaData.builder()
            .modSeq(modSeq)
            .flags(createFlags())
            .composedMessageId(new ComposedMessageId(mailboxId, getMessageId(), uid))
            .build();
    }

    public Date getInternalDate() {
        return internalDate;
    }

    public TestId getMailboxId() {
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

    public void unsetRecent() {
        recent = false;
    }

    @Override
    public long getHeaderOctets() {
        return size - body.length;
    }

    public void setFlags(Flags flags) {
        answered = flags.contains(Flags.Flag.ANSWERED);
        deleted = flags.contains(Flags.Flag.DELETED);
        draft = flags.contains(Flags.Flag.DRAFT);
        flagged = flags.contains(Flags.Flag.FLAGGED);
        recent = flags.contains(Flags.Flag.RECENT);
        seen = flags.contains(Flags.Flag.SEEN);
    }

    public Flags createFlags() {
        final Flags flags = new Flags();

        if (isAnswered()) {
            flags.add(Flags.Flag.ANSWERED);
        }
        if (isDeleted()) {
            flags.add(Flags.Flag.DELETED);
        }
        if (isDraft()) {
            flags.add(Flags.Flag.DRAFT);
        }
        if (isFlagged()) {
            flags.add(Flags.Flag.FLAGGED);
        }
        if (isRecent()) {
            flags.add(Flags.Flag.RECENT);
        }
        if (isSeen()) {
            flags.add(Flags.Flag.SEEN);
        }
        return flags;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mailboxId.id, uid);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleMailboxMembership) {
            SimpleMailboxMembership other = (SimpleMailboxMembership) obj;
            return Objects.equal(this.mailboxId.id, other.mailboxId.id)
                    && Objects.equal(this.uid, other.uid);
        }
        return false;
    }

    public String toString() {
        return "mailbox("
        + "mailboxId = " + this.mailboxId + TOSTRING_SEPARATOR
        + "uid = " + this.uid + TOSTRING_SEPARATOR
        + "internalDate = " + this.internalDate + TOSTRING_SEPARATOR
        + "size = " + this.size + TOSTRING_SEPARATOR
        + "answered = " + this.answered + TOSTRING_SEPARATOR
        + "deleted = " + this.deleted + TOSTRING_SEPARATOR
        + "draft = " + this.draft + TOSTRING_SEPARATOR
        + "flagged = " + this.flagged + TOSTRING_SEPARATOR
        + "recent = " + this.recent + TOSTRING_SEPARATOR
        + "seen = " + this.seen + TOSTRING_SEPARATOR
        + " )";
    }

    
    public static final char[] NEW_LINE = { 0x0D, 0x0A };
    
    public byte[] body;
    public Map<String, String> headers;
    public List<SimpleProperty> properties;
    public String subType = null;
    public String mediaType = null;
    public Long textualLineCount = null;

    private final int size;

    private long modSeq;
    

    public InputStream getBodyContent() throws IOException {
        return new ByteArrayInputStream(body);
    }

    public InputStream getHeaderContent() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Writer writer = new OutputStreamWriter(baos, "us-ascii");

        Iterator<Entry<String, String>> hIt = headers.entrySet().iterator();
        while (hIt.hasNext()) {
            Entry<String, String> header = hIt.next();
            writer.write(header.getKey());
            writer.write(": ");
            writer.write(header.getValue());
            writer.write(NEW_LINE);
        }
        writer.write(NEW_LINE);
        writer.flush();
        return new ByteArrayInputStream(baos.toByteArray());

    }

    public long getBodyOctets() {
        return body.length;
    }

    public String getSubType() {
        return subType;
    }

    public String getMediaType() {
        return mediaType;
    }

    public List<Property> getProperties() {
        if (properties != null) {
            return ImmutableList.<Property>copyOf(properties);
        } else {
            return ImmutableList.of();
        }
    }

    public Long getTextualLineCount() {
        return textualLineCount;
    }

    public long getFullContentOctets() {
        return size;
    }

    public int compareTo(MailboxMessage other) {
        return getUid().compareTo(other.getUid());
    }

    public long getModSeq() {
        return modSeq;
    }

    public void setModSeq(long modSeq) {
        this.modSeq = modSeq;
    }

    @Override
    public void setUid(MessageUid uid) {
        this.uid = uid;
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return new SequenceInputStream(getHeaderContent(), getBodyContent());
    }

    @Override
    public MessageId getMessageId() {
        return messageId;
    }

    @Override
    public List<MessageAttachment> getAttachments() {
        throw new NotImplementedException("Attachments Ids not implemented");
    }
    
}
