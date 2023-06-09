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
package org.apache.james.mailbox.jpa.mail.model.openjpa;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.Flags;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.JPAProperty;
import org.apache.james.mailbox.jpa.mail.model.JPAUserFlag;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.DelegatingMailboxMessage;
import org.apache.james.mailbox.store.mail.model.FlagsFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.openjpa.persistence.jdbc.ElementJoinColumn;
import org.apache.openjpa.persistence.jdbc.ElementJoinColumns;
import org.apache.openjpa.persistence.jdbc.Index;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Abstract base class for JPA based implementations of
 * {@link DelegatingMailboxMessage}
 */
@IdClass(AbstractJPAMailboxMessage.MailboxIdUidKey.class)
@NamedQueries({
        @NamedQuery(name = "findRecentMessageUidsInMailbox", query = "SELECT message.uid FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.recent = TRUE ORDER BY message.uid ASC"),
        @NamedQuery(name = "listUidsInMailbox", query = "SELECT message.uid FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam ORDER BY message.uid ASC"),
        @NamedQuery(name = "findUnseenMessagesInMailboxOrderByUid", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.seen = FALSE ORDER BY message.uid ASC"),
        @NamedQuery(name = "findMessagesInMailbox", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam ORDER BY message.uid ASC"),
        @NamedQuery(name = "findMessagesInMailboxBetweenUIDs", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid BETWEEN :fromParam AND :toParam ORDER BY message.uid ASC"),
        @NamedQuery(name = "findMessagesInMailboxWithUID", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid=:uidParam ORDER BY message.uid ASC"),
        @NamedQuery(name = "findMessagesInMailboxAfterUID", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid>=:uidParam ORDER BY message.uid ASC"),
        @NamedQuery(name = "findDeletedMessagesInMailbox", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.deleted=TRUE ORDER BY message.uid ASC"),
        @NamedQuery(name = "findDeletedMessagesInMailboxBetweenUIDs", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid BETWEEN :fromParam AND :toParam AND message.deleted=TRUE ORDER BY message.uid ASC"),
        @NamedQuery(name = "findDeletedMessagesInMailboxWithUID", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid=:uidParam AND message.deleted=TRUE ORDER BY message.uid ASC"),
        @NamedQuery(name = "findDeletedMessagesInMailboxAfterUID", query = "SELECT message FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid>=:uidParam AND message.deleted=TRUE ORDER BY message.uid ASC"),

        @NamedQuery(name = "deleteMessagesInMailbox", query = "DELETE FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam"),
        @NamedQuery(name = "deleteMessagesInMailboxBetweenUIDs", query = "DELETE FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid BETWEEN :fromParam AND :toParam"),
        @NamedQuery(name = "deleteMessagesInMailboxWithUID", query = "DELETE FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid=:uidParam"),
        @NamedQuery(name = "deleteMessagesInMailboxAfterUID", query = "DELETE FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.uid>=:uidParam"),

        @NamedQuery(name = "countUnseenMessagesInMailbox", query = "SELECT COUNT(message) FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam AND message.seen=FALSE"),
        @NamedQuery(name = "countMessagesInMailbox", query = "SELECT COUNT(message) FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam"),
        @NamedQuery(name = "deleteMessages", query = "DELETE FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam"),
        @NamedQuery(name = "findLastUidInMailbox", query = "SELECT message.uid FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam ORDER BY message.uid DESC"),
        @NamedQuery(name = "findHighestModSeqInMailbox", query = "SELECT message.modSeq FROM MailboxMessage message WHERE message.mailbox.mailboxId = :idParam ORDER BY message.modSeq DESC")
})
@MappedSuperclass
public abstract class AbstractJPAMailboxMessage implements MailboxMessage {
    private static final String TOSTRING_SEPARATOR = " ";

    /** Identifies composite key */
    @Embeddable
    public static class MailboxIdUidKey implements Serializable {

        private static final long serialVersionUID = 7847632032426660997L;

        public MailboxIdUidKey() {
        }

        /** The value for the mailbox field */
        public long mailbox;

        /** The value for the uid field */
        public long uid;

        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + (int) (mailbox ^ (mailbox >>> 32));
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
            final MailboxIdUidKey other = (MailboxIdUidKey) obj;
            if (mailbox != other.mailbox) {
                return false;
            }
            if (uid != other.uid) {
                return false;
            }
            return true;
        }

    }

    /** The value for the mailboxId field */
    @Id
    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE }, fetch = FetchType.EAGER)
    @Column(name = "MAILBOX_ID", nullable = true)
    private JPAMailbox mailbox;

    /** The value for the uid field */
    @Id
    @Column(name = "MAIL_UID")
    private long uid;

    /** The value for the modSeq field */
    @Index
    @Column(name = "MAIL_MODSEQ")
    private long modSeq;

    /** The value for the internalDate field */
    @Basic(optional = false)
    @Column(name = "MAIL_DATE")
    private Date internalDate;

    /** The value for the answered field */
    @Basic(optional = false)
    @Column(name = "MAIL_IS_ANSWERED", nullable = false)
    private boolean answered = false;

    /** The value for the deleted field */
    @Basic(optional = false)
    @Column(name = "MAIL_IS_DELETED", nullable = false)
    @Index
    private boolean deleted = false;

    /** The value for the draft field */
    @Basic(optional = false)
    @Column(name = "MAIL_IS_DRAFT", nullable = false)
    private boolean draft = false;

    /** The value for the flagged field */
    @Basic(optional = false)
    @Column(name = "MAIL_IS_FLAGGED", nullable = false)
    private boolean flagged = false;

    /** The value for the recent field */
    @Basic(optional = false)
    @Column(name = "MAIL_IS_RECENT", nullable = false)
    @Index
    private boolean recent = false;

    /** The value for the seen field */
    @Basic(optional = false)
    @Column(name = "MAIL_IS_SEEN", nullable = false)
    @Index
    private boolean seen = false;

    /** The first body octet */
    @Basic(optional = false)
    @Column(name = "MAIL_BODY_START_OCTET", nullable = false)
    private int bodyStartOctet;

    /** Number of octets in the full document content */
    @Basic(optional = false)
    @Column(name = "MAIL_CONTENT_OCTETS_COUNT", nullable = false)
    private long contentOctets;

    /** MIME media type */
    @Basic(optional = true)
    @Column(name = "MAIL_MIME_TYPE", nullable = true, length = 200)
    private String mediaType;

    /** MIME sub type */
    @Basic(optional = true)
    @Column(name = "MAIL_MIME_SUBTYPE", nullable = true, length = 200)
    private String subType;

    /** THE CRFL count when this document is textual, null otherwise */
    @Basic(optional = true)
    @Column(name = "MAIL_TEXTUAL_LINE_COUNT", nullable = true)
    private Long textualLineCount;

    /** Meta data for this message */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("line")
    @ElementJoinColumns({ @ElementJoinColumn(name = "MAILBOX_ID", referencedColumnName = "MAILBOX_ID"),
            @ElementJoinColumn(name = "MAIL_UID", referencedColumnName = "MAIL_UID") })
    private List<JPAProperty> properties;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderBy("id")
    @ElementJoinColumns({ @ElementJoinColumn(name = "MAILBOX_ID", referencedColumnName = "MAILBOX_ID"),
            @ElementJoinColumn(name = "MAIL_UID", referencedColumnName = "MAIL_UID") })
    private List<JPAUserFlag> userFlags;

    public AbstractJPAMailboxMessage() {

    }

    public AbstractJPAMailboxMessage(JPAMailbox mailbox, Date internalDate, Flags flags, long contentOctets,
            int bodyStartOctet, PropertyBuilder propertyBuilder) {
        this.mailbox = mailbox;
        this.internalDate = internalDate;
        userFlags = new ArrayList<>();

        setFlags(flags);
        this.contentOctets = contentOctets;
        this.bodyStartOctet = bodyStartOctet;
        Properties properties = propertyBuilder.build();
        this.textualLineCount = properties.getTextualLineCount();
        this.mediaType = properties.getMediaType();
        this.subType = properties.getSubType();
        final List<Property> propertiesAsList = properties.toProperties();
        this.properties = new ArrayList<>(propertiesAsList.size());
        int order = 0;
        for (Property property : propertiesAsList) {
            this.properties.add(new JPAProperty(property, order++));
        }

    }

    /**
     * Constructs a copy of the given message. All properties are cloned except
     * mailbox and UID.
     *
     * @param mailbox
     *            new mailbox
     * @param uid
     *            new UID
     * @param modSeq
     *            new modSeq
     * @param original
     *            message to be copied, not null
     */
    public AbstractJPAMailboxMessage(JPAMailbox mailbox, MessageUid uid, ModSeq modSeq, MailboxMessage original)
            throws MailboxException {
        super();
        this.mailbox = mailbox;
        this.uid = uid.asLong();
        this.modSeq = modSeq.asLong();
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
        final List<Property> properties = original.getProperties().toProperties();
        this.properties = new ArrayList<>(properties.size());
        int order = 0;
        for (Property property : properties) {
            this.properties.add(new JPAProperty(property, order++));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getMailboxId().getRawId(), uid);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AbstractJPAMailboxMessage) {
            AbstractJPAMailboxMessage other = (AbstractJPAMailboxMessage) obj;
            return Objects.equal(getMailboxId(), other.getMailboxId())
                    && Objects.equal(uid, other.getUid());
        }
        return false;
    }

    @Override
    public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
        return ComposedMessageIdWithMetaData.builder()
            .modSeq(getModSeq())
            .flags(createFlags())
            .composedMessageId(new ComposedMessageId(mailbox.getMailboxId(), getMessageId(), MessageUid.of(uid)))
            .threadId(getThreadId())
            .build();
    }

    @Override
    public ModSeq getModSeq() {
        return ModSeq.of(modSeq);
    }

    @Override
    public void setModSeq(ModSeq modSeq) {
        this.modSeq = modSeq.asLong();
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String getSubType() {
        return subType;
    }

    /**
     * Gets a read-only list of meta-data properties. For properties with
     * multiple values, this list will contain several enteries with the same
     * namespace and local name.
     *
     * @return unmodifiable list of meta-data, not null
     */
    @Override
    public Properties getProperties() {
        return new PropertyBuilder(properties.stream()
            .map(JPAProperty::toProperty)
            .collect(ImmutableList.toImmutableList()))
            .build();
    }

    @Override
    public Long getTextualLineCount() {
        return textualLineCount;
    }

    @Override
    public long getFullContentOctets() {
        return contentOctets;
    }

    protected int getBodyStartOctet() {
        return bodyStartOctet;
    }

    @Override
    public Date getInternalDate() {
        return internalDate;
    }

    @Override
    public JPAId getMailboxId() {
        return getMailbox().getMailboxId();
    }

    @Override
    public MessageUid getUid() {
        return MessageUid.of(uid);
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
        this.uid = uid.asLong();
    }

    @Override
    public void setSaveDate(Date saveDate) {

    }

    @Override
    public long getHeaderOctets() {
        return bodyStartOctet;
    }

    @Override
    public void setFlags(Flags flags) {
        answered = flags.contains(Flags.Flag.ANSWERED);
        deleted = flags.contains(Flags.Flag.DELETED);
        draft = flags.contains(Flags.Flag.DRAFT);
        flagged = flags.contains(Flags.Flag.FLAGGED);
        recent = flags.contains(Flags.Flag.RECENT);
        seen = flags.contains(Flags.Flag.SEEN);

        String[] userflags = flags.getUserFlags();
        userFlags.clear();
        for (String userflag : userflags) {
            userFlags.add(new JPAUserFlag(userflag));
        }
    }

    /**
     * Utility getter on Mailbox.
     */
    public JPAMailbox getMailbox() {
        return mailbox;
    }

    @Override
    public Flags createFlags() {
        return FlagsFactory.createFlags(this, createUserFlags());
    }

    protected String[] createUserFlags() {
        return userFlags.stream()
            .map(JPAUserFlag::getName)
            .toArray(String[]::new);
    }

    /**
     * Utility setter on Mailbox.
     */
    public void setMailbox(JPAMailbox mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return new SequenceInputStream(getHeaderContent(), getBodyContent());
    }

    @Override
    public long getBodyOctets() {
        return getFullContentOctets() - getBodyStartOctet();
    }

    @Override
    public MessageId getMessageId() {
        return new DefaultMessageId();
    }

    @Override
    public ThreadId getThreadId() {
        return new ThreadId(getMessageId());
    }

    @Override
    public Optional<Date> getSaveDate() {
        return Optional.empty();
    }

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
    public List<MessageAttachmentMetadata> getAttachments() {
        try {
            AtomicInteger counter = new AtomicInteger(0);
            MessageParser.ParsingResult parsingResult = new MessageParser().retrieveAttachments(getFullContent());
            ImmutableList<MessageAttachmentMetadata> result = parsingResult
                .getAttachments()
                .stream()
                .map(Throwing.<ParsedAttachment, MessageAttachmentMetadata>function(
                    attachmentMetadata -> attachmentMetadata.asMessageAttachment(generateFixedAttachmentId(counter.incrementAndGet()), getMessageId()))
                    .sneakyThrow())
                .collect(ImmutableList.toImmutableList());
            parsingResult.dispose();
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AttachmentId generateFixedAttachmentId(int position) {
        return AttachmentId.from(getMailboxId().serialize() + "-" + getUid().asLong() + "-" + position);
    }
}
