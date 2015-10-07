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
package org.apache.james.mailbox.hbase.mail;

import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_BODY_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_HEADERS_CF;
import static org.apache.james.mailbox.hbase.HBaseUtils.messageRowKey;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.hadoop.conf.Configuration;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.hbase.io.ChunkInputStream;
import org.apache.james.mailbox.store.mail.model.AbstractMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

/**
 * Concrete HBaseMessage implementation. This implementation does not store any
 * message content. The message content is retrieved using a ChunkedInputStream
 * directly from HBase.
 */
public class HBaseMessage extends AbstractMessage<HBaseId> {

    private static final String TOSTRING_SEPARATOR = " ";
    /** Configuration for the HBase cluster */
    private final Configuration conf;
    /** The value for the mailboxId field */
    private HBaseId mailboxId;
    /** The value for the uid field */
    private long uid;
    /** The value for the modSeq field */
    private long modSeq;
    /** The value for the internalDate field */
    private Date internalDate;
    /** The value for the answered field */
    private boolean answered = false;
    /** The value for the deleted field */
    private boolean deleted = false;
    /** The value for the draft field */
    private boolean draft = false;
    /** The value for the flagged field */
    private boolean flagged = false;
    /** The value for the recent field */
    private boolean recent = false;
    /** The value for the seen field */
    private boolean seen = false;
    /** The first body octet */
    private int bodyStartOctet;
    /** Number of octets in the full document content */
    private long contentOctets;
    /** MIME media type */
    private String mediaType;
    /** MIME sub type */
    private String subType;
    /** THE CRFL count when this document is textual, null otherwise */
    private Long textualLineCount;
    /** Meta data for this message */
    private List<Property> properties;
    private List<String> userFlags;

    /**
     * Create a copy of the given message.
     * All properties are cloned except mailbox and UID.
     * @param mailboxId
     * @param uid
     * @param modSeq
     * @param original
     * @throws MailboxException
     */
    public HBaseMessage(Configuration conf, HBaseId mailboxId, long uid, long modSeq, Message<?> original) throws MailboxException {
        super();
        this.conf = conf;
        this.mailboxId = mailboxId;
        this.uid = uid;
        this.modSeq = modSeq;
        this.userFlags = new ArrayList<String>();
        setFlags(original.createFlags());

        // A copy of a message is recent
        // See MAILBOX-85
        this.recent = true;

        this.contentOctets = original.getFullContentOctets();
        this.bodyStartOctet = (int) (original.getFullContentOctets() - original.getBodyOctets());
        this.internalDate = original.getInternalDate();

        this.textualLineCount = original.getTextualLineCount();
        this.mediaType = original.getMediaType();
        this.subType = original.getSubType();
        this.properties = original.getProperties();
    }

    /**
     * Create a copy of the given message.
     * @param mailboxId
     * @param internalDate
     * @param flags
     * @param contentOctets
     * @param bodyStartOctet
     * @param propertyBuilder
     */
    public HBaseMessage(Configuration conf, HBaseId mailboxId, Date internalDate, Flags flags, long contentOctets, int bodyStartOctet, PropertyBuilder propertyBuilder) {
        super();
        this.conf = conf;
        this.mailboxId = mailboxId;
        this.internalDate = internalDate;
        userFlags = new ArrayList<String>();

        setFlags(flags);
        this.contentOctets = contentOctets;
        this.bodyStartOctet = bodyStartOctet;
        this.textualLineCount = propertyBuilder.getTextualLineCount();
        this.mediaType = propertyBuilder.getMediaType();
        this.subType = propertyBuilder.getSubType();
        this.properties = propertyBuilder.toProperties();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#getBodyContent()
     */
    @Override
    public InputStream getBodyContent() throws IOException {
        return new ChunkInputStream(conf, MESSAGES_TABLE, MESSAGE_DATA_BODY_CF, messageRowKey(this));
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#getHeaderContent()
     */
    @Override
    public InputStream getHeaderContent() throws IOException {
        return new ChunkInputStream(conf, MESSAGES_TABLE, MESSAGE_DATA_HEADERS_CF, messageRowKey(this));
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + mailboxId.hashCode();
        result = PRIME * result + (int) (uid ^ (uid >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HBaseMessage other = (HBaseMessage) obj;
        if (getMailboxId() != null) {
            if (!getMailboxId().equals(other.getMailboxId())) {
                return false;
            }
        } else {
            if (other.getMailboxId() != null) {
                return false;
            }
        }
        if (uid != other.uid) {
            return false;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#getModSeq()
     */
    @Override
    public long getModSeq() {
        return modSeq;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#setModSeq(long)
     */
    @Override
    public void setModSeq(long modSeq) {
        this.modSeq = modSeq;
    }

    /**
     * Gets the top level MIME content media type.
     *
     * @return top level MIME content media type, or null if default
     */
    @Override
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Gets the MIME content subtype.
     *
     * @return the MIME content subtype, or null if default
     */
    @Override
    public String getSubType() {
        return subType;
    }

    /**
     * Gets a read-only list of meta-data properties.
     * For properties with multiple values, this list will contain
     * several enteries with the same namespace and local name.
     * @return unmodifiable list of meta-data, not null
     */
    @Override
    public List<Property> getProperties() {
        return new ArrayList<Property>(properties);
    }

    /**
     * Gets the number of CRLF in a textual document.
     * @return CRLF count when document is textual,
     * null otherwise
     */
    @Override
    public Long getTextualLineCount() {
        return textualLineCount;
    }

    public void setTextualLineCount(Long textualLineCount) {
        this.textualLineCount = textualLineCount;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Document#getFullContentOctets()
     */
    @Override
    public long getFullContentOctets() {
        return contentOctets;
    }

    @Override
    protected int getBodyStartOctet() {
        return bodyStartOctet;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#getInternalDate()
     */
    @Override
    public Date getInternalDate() {
        return internalDate;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#getMailboxId()
     */
    @Override
    public HBaseId getMailboxId() {
        return mailboxId;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#getUid()
     */
    @Override
    public long getUid() {
        return uid;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#isAnswered()
     */
    @Override
    public boolean isAnswered() {
        return answered;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#isDeleted()
     */
    @Override
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#isDraft()
     */
    @Override
    public boolean isDraft() {
        return draft;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#isFlagged()
     */
    @Override
    public boolean isFlagged() {
        return flagged;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#isRecent()
     */
    @Override
    public boolean isRecent() {
        return recent;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#isSeen()
     */
    @Override
    public boolean isSeen() {
        return seen;
    }

    @Override
    public void setUid(long uid) {
        this.uid = uid;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#setFlags(javax.mail.Flags)
     */
    @Override
    public final void setFlags(Flags flags) {
        answered = flags.contains(Flags.Flag.ANSWERED);
        deleted = flags.contains(Flags.Flag.DELETED);
        draft = flags.contains(Flags.Flag.DRAFT);
        flagged = flags.contains(Flags.Flag.FLAGGED);
        recent = flags.contains(Flags.Flag.RECENT);
        seen = flags.contains(Flags.Flag.SEEN);
        String[] userflags = flags.getUserFlags();
        userFlags.clear();
        userFlags.addAll(Arrays.asList(userflags));
    }

    /**
     * This implementation supports user flags
     *
     *
     */
    @Override
    public String[] createUserFlags() {
        String[] flags = new String[userFlags.size()];
        for (int i = 0; i < userFlags.size(); i++) {
            flags[i] = userFlags.get(i);
        }
        return flags;
    }

    @Override
    public String toString() {
        final String retValue =
                "message("
                + "mailboxId = " + this.getMailboxId() + TOSTRING_SEPARATOR
                + "uid = " + this.uid + TOSTRING_SEPARATOR
                + "internalDate = " + this.internalDate + TOSTRING_SEPARATOR
                + "answered = " + this.answered + TOSTRING_SEPARATOR
                + "deleted = " + this.deleted + TOSTRING_SEPARATOR
                + "draft = " + this.draft + TOSTRING_SEPARATOR
                + "flagged = " + this.flagged + TOSTRING_SEPARATOR
                + "recent = " + this.recent + TOSTRING_SEPARATOR
                + "seen = " + this.seen + TOSTRING_SEPARATOR
                + " )";
        return retValue;
    }
}
