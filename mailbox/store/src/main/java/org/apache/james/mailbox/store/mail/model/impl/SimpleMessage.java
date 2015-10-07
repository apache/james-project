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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.AbstractMessage;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;

public class SimpleMessage<Id extends MailboxId> extends AbstractMessage<Id> {

    private long uid;
    private final Id mailboxId;
    private long size;
    private boolean answered;
    private boolean deleted;
    private boolean draft;
    private boolean flagged;
    private boolean recent;
    private boolean seen;
    private String[] userFlags;
    private Date internalDate;
    private final String subType;
    private List<Property> properties;
    private final String mediaType;
    private Long lineCount;
    private int bodyStartOctet;
    private long modSeq;
    private SharedInputStream content;

    public SimpleMessage(Date internalDate, int size, int bodyStartOctet,
            SharedInputStream content, Flags flags,
            PropertyBuilder propertyBuilder, final Id mailboxId) {
        this.content = content;

        this.size = size;
        this.bodyStartOctet = bodyStartOctet;
        setFlags(flags);
        lineCount = propertyBuilder.getTextualLineCount();
        this.internalDate = internalDate;
        this.mailboxId = mailboxId;
        this.properties = propertyBuilder.toProperties();
        this.mediaType = propertyBuilder.getMediaType();
        this.subType = propertyBuilder.getSubType();
        this.userFlags = flags.getUserFlags();
    }

    public SimpleMessage(Mailbox<Id> mailbox, Message<Id> original)
            throws MailboxException {
        this.internalDate = original.getInternalDate();
        this.size = original.getFullContentOctets();
        this.mailboxId = mailbox.getMailboxId();
        setFlags(original.createFlags());
        try {
            this.content = new SharedByteArrayInputStream(
                    IOUtils.toByteArray(original.getFullContent()));
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        }

        this.bodyStartOctet = (int) (original.getFullContentOctets() - original
                .getBodyOctets());

        PropertyBuilder pBuilder = new PropertyBuilder(original.getProperties());
        this.lineCount = original.getTextualLineCount();
        this.mediaType = original.getMediaType();
        this.subType = original.getSubType();
        final List<Property> properties = pBuilder.toProperties();
        this.properties = new ArrayList<Property>(properties.size());
        for (final Property property : properties) {
            this.properties.add(new SimpleProperty(property));
        }
    }

    @Override
    protected String[] createUserFlags() {
        return userFlags.clone();
    }

    public Date getInternalDate() {
        return internalDate;
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

    public synchronized void setFlags(Flags flags) {
        answered = flags.contains(Flags.Flag.ANSWERED);
        deleted = flags.contains(Flags.Flag.DELETED);
        draft = flags.contains(Flags.Flag.DRAFT);
        flagged = flags.contains(Flags.Flag.FLAGGED);
        recent = flags.contains(Flags.Flag.RECENT);
        seen = flags.contains(Flags.Flag.SEEN);
        userFlags = flags.getUserFlags();
    }

    public InputStream getBodyContent() throws IOException {
        return content.newStream(getBodyStartOctet(), -1);
    }

    public long getFullContentOctets() {
        return size;
    }

    public String getMediaType() {
        return mediaType;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public String getSubType() {
        return subType;
    }

    public Long getTextualLineCount() {
        return lineCount;
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
        final SimpleMessage<Id> other = (SimpleMessage<Id>) obj;
        if (uid != other.uid)
            return false;
        return true;
    }

    /**
     * Representation suitable for logging and debugging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        return super.toString() + "[" + "uid = " + this.uid + " "
                + "mailboxId = " + this.mailboxId + " " + "size = " + this.size
                + " " + "answered = " + this.answered + " " + "deleted = "
                + this.deleted + " " + "draft = " + this.draft + " "
                + "flagged = " + this.flagged + " " + "recent = " + this.recent
                + " " + "seen = " + this.seen + " " + "internalDate = "
                + this.internalDate + " " + "subType = " + this.subType + " "
                + "mediaType = " + this.mediaType + " " + " ]";
    }

    @Override
    protected int getBodyStartOctet() {
        return bodyStartOctet;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#getModSeq()
     */
    public long getModSeq() {
        return modSeq;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#setModSeq(long)
     */
    public void setModSeq(long modSeq) {
        this.modSeq = modSeq;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#setUid(long)
     */
    public void setUid(long uid) {
        this.uid = uid;
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        long headerEnd = getBodyStartOctet();
        if (headerEnd < 0) {
            headerEnd = 0;
        }
        return content.newStream(0, headerEnd);
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return content.newStream(0, -1);
    }

}
