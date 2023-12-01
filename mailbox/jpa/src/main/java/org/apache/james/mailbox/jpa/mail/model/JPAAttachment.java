/***************************************************************
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

package org.apache.james.mailbox.jpa.mail.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@Entity(name = "Attachment")
@Table(name = "JAMES_ATTACHMENT")
@NamedQuery(name = "findAttachmentById", query = "SELECT attachment FROM Attachment attachment WHERE attachment.attachmentId = :idParam")
public class JPAAttachment {

    private static final String TOSTRING_SEPARATOR = " ";
    private static final byte[] EMPTY_ARRAY = new byte[]{};

    @Id
    @GeneratedValue
    @Column(name = "ATTACHMENT_ID", nullable = false)
    private String attachmentId;

    @Basic(optional = false)
    @Column(name = "TYPE", nullable = false)
    private String type;

    @Basic(optional = false)
    @Column(name = "SIZE", nullable = false)
    private long size;

    @Basic(optional = false, fetch = FetchType.LAZY)
    @Column(name = "CONTENT", length = 1048576000, nullable = false)
    @Lob
    private byte[] content;

    @Basic(optional = true)
    @Column(name = "NAME")
    private String name;

    @Basic(optional = true)
    @Column(name = "CID")
    private String cid;

    @Basic(optional = false)
    @Column(name = "INLINE", nullable = false)
    private boolean isInline;

    public JPAAttachment() {
    }

    public JPAAttachment(MessageAttachmentMetadata messageAttachmentMetadata, byte[] bytes) {
        setMetadata(messageAttachmentMetadata, bytes);
    }

    public JPAAttachment(MessageAttachmentMetadata messageAttachmentMetadata) {
        setMetadata(messageAttachmentMetadata, new byte[0]);
    }

    private void setMetadata(MessageAttachmentMetadata messageAttachmentMetadata, byte[] bytes) {
        this.name = messageAttachmentMetadata.getName().orElse(null);
        messageAttachmentMetadata.getCid()
           .ifPresentOrElse(c -> this.cid = c.getValue(), () -> this.cid = "");
        this.type = messageAttachmentMetadata.getAttachment().getType().asString();
        this.size = messageAttachmentMetadata.getAttachment().getSize();
        this.isInline = messageAttachmentMetadata.isInline();
        this.content = bytes;
    }

    public AttachmentMetadata toAttachmentMetadata() {
        return AttachmentMetadata.builder()
            .attachmentId(AttachmentId.from(attachmentId))
            .messageId(new DefaultMessageId())
            .type(type)
            .size(size)
            .build();
    }

    public MessageAttachmentMetadata toMessageAttachmentMetadata() {
        return MessageAttachmentMetadata.builder()
            .attachment(toAttachmentMetadata())
            .name(Optional.ofNullable(name))
            .cid(Optional.of(Cid.from(cid)))
            .isInline(isInline)
            .build();
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public String getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public String getName() {
        return name;
    }

    public boolean isInline() {
        return isInline;
    }

    public String getCid() {
        return cid;
    }

    public InputStream getContent() {
        return new ByteArrayInputStream(Objects.requireNonNullElse(content, EMPTY_ARRAY));
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setContent(byte[] bytes) {
        this.content = bytes;
    }

    @Override
    public String toString() {
        return "Attachment ( "
            + "attachmentId = " + this.attachmentId + TOSTRING_SEPARATOR
            + "name = " + this.type + TOSTRING_SEPARATOR
            + "type = " + this.type + TOSTRING_SEPARATOR
            + "size = " + this.size + TOSTRING_SEPARATOR
            + "cid = " + this.cid + TOSTRING_SEPARATOR
            + "isInline = " + this.isInline + TOSTRING_SEPARATOR
            + " )";
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof JPAAttachment) {
            JPAAttachment that = (JPAAttachment) o;

            return Objects.equals(this.size, that.size)
                && Objects.equals(this.attachmentId, that.attachmentId)
                && Objects.equals(this.cid, that.cid)
                && Arrays.equals(this.content, that.content)
                && Objects.equals(this.isInline, that.isInline)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.type, that.type);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(attachmentId, type, size, name, cid, isInline);
    }
}
