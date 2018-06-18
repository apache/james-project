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
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.hadoop.conf.Configuration;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.hbase.io.ChunkInputStream;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.FlagsFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.search.comparator.UidComparator;
import org.apache.james.mime4j.MimeException;

import com.google.common.base.Objects;

/**
 * Concrete HBaseMailboxMessage implementation. This implementation does not store any
 * message content. The message content is retrieved using a ChunkedInputStream
 * directly from HBase.
 */
public class HBaseMailboxMessage implements MailboxMessage {

    private static final Comparator<MailboxMessage> MESSAGE_UID_COMPARATOR = new UidComparator();
    private static final String TOSTRING_SEPARATOR = " ";
    /** Configuration for the HBase cluster */
    private final Configuration conf;
    /** The value for the mailboxId field */
    private final HBaseId mailboxId;
    /** The value for the uid field */
    private MessageUid uid;
    /** The value for the modSeq field */
    private long modSeq;
    /** The value for the internalDate field */
    private final Date internalDate;
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
    private final int bodyStartOctet;
    /** Number of octets in the full document content */
    private final long contentOctets;
    /** MIME media type */
    private final String mediaType;
    /** MIME sub type */
    private final String subType;
    /** THE CRFL count when this document is textual, null otherwise */
    private Long textualLineCount;
    /** Meta data for this message */
    private final List<Property> properties;
    private final List<String> userFlags;
    private final MessageId messageId;
    
    /**
     * Create a copy of the given message.
     * All properties are cloned except mailbox and UID.
     */
    public HBaseMailboxMessage(Configuration conf, HBaseId mailboxId, MessageUid uid, MessageId messageId, long modSeq, MailboxMessage original) throws MailboxException {
        this.conf = conf;
        this.mailboxId = mailboxId;
        this.uid = uid;
        this.messageId = messageId;
        this.modSeq = modSeq;
        this.userFlags = new ArrayList<>();
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

    public HBaseMailboxMessage(Configuration conf, HBaseId mailboxId, MessageId messageId,
            Date internalDate, Flags flags, long contentOctets, int bodyStartOctet, PropertyBuilder propertyBuilder) {
        super();
        this.conf = conf;
        this.mailboxId = mailboxId;
        this.messageId = messageId;
        this.internalDate = internalDate;
        userFlags = new ArrayList<>();

        setFlags(flags);
        this.contentOctets = contentOctets;
        this.bodyStartOctet = bodyStartOctet;
        this.textualLineCount = propertyBuilder.getTextualLineCount();
        this.mediaType = propertyBuilder.getMediaType();
        this.subType = propertyBuilder.getSubType();
        this.properties = propertyBuilder.toProperties();
    }

    @Override
    public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
        return ComposedMessageIdWithMetaData.builder()
            .modSeq(modSeq)
            .flags(createFlags())
            .composedMessageId(new ComposedMessageId(mailboxId, getMessageId(), uid))
            .build();
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        return new ChunkInputStream(conf, MESSAGES_TABLE, MESSAGE_DATA_BODY_CF, messageRowKey(this));
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        return new ChunkInputStream(conf, MESSAGES_TABLE, MESSAGE_DATA_HEADERS_CF, messageRowKey(this));
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return new SequenceInputStream(getHeaderContent(), getBodyContent());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mailboxId, uid);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HBaseMailboxMessage) {
            HBaseMailboxMessage other = (HBaseMailboxMessage) obj;
            return Objects.equal(this.mailboxId, other.mailboxId) &&
                    Objects.equal(this.uid, other.uid);
        }
        return false;
    }

    @Override
    public long getModSeq() {
        return modSeq;
    }

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

    @Override
    public long getBodyOctets() {
        return getFullContentOctets() - bodyStartOctet;
    }

    /**
     * Gets a read-only list of meta-data properties.
     * For properties with multiple values, this list will contain
     * several enteries with the same namespace and local name.
     * @return unmodifiable list of meta-data, not null
     */
    @Override
    public List<Property> getProperties() {
        return new ArrayList<>(properties);
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

    @Override
    public long getFullContentOctets() {
        return contentOctets;
    }

    @Override
    public MessageId getMessageId() {
        return messageId;
    }

    @Override
    public Date getInternalDate() {
        return internalDate;
    }

    @Override
    public HBaseId getMailboxId() {
        return mailboxId;
    }

    @Override
    public MessageUid getUid() {
        return uid;
    }

    @Override
    public boolean isAnswered() {
        return answered;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public boolean isDraft() {
        return draft;
    }

    @Override
    public boolean isFlagged() {
        return flagged;
    }

    @Override
    public boolean isRecent() {
        return recent;
    }

    @Override
    public boolean isSeen() {
        return seen;
    }

    @Override
    public void setUid(MessageUid uid) {
        this.uid = uid;
    }

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

    @Override
    public Flags createFlags() {
        return FlagsFactory.createFlags(this, createUserFlags());
    }

    @Override
    public long getHeaderOctets() {
        return bodyStartOctet;
    }

    /**
     * This implementation supports user flags
     */
    public String[] createUserFlags() {
        return userFlags.toArray(new String[userFlags.size()]);
    }

    @Override
    public String toString() {
        return "message("
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
    }

    @Override
    public int compareTo(MailboxMessage other) {
        return MESSAGE_UID_COMPARATOR.compare(this, other);
    }

    @Override
    public List<MessageAttachment> getAttachments() {
        try {
            return new MessageParser().retrieveAttachments(getFullContent());
        } catch (MimeException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
