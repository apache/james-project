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

package org.apache.james.mailbox.jpa.mail.model;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;


@Entity(name = "Attachment")
@Table(name = "JAMES_MAIL_ATTACHMENT")
public class JPAAttachment {

    public static final String ATTACHMENT_ID = "ATTACHMENT_ID";
    public static final String ATTACHMENT_CONTENT = "CONTENT";
    public static final String ATTACHMENT_TYPE = "TYPE";
    public static final String ATTACHMENT_OWNERS = "OWNERS";
    public static final String ATTACHMENT_MESSAGE_IDS = "MESSAGE_IDS";
    public static final String ATTACHMENT_CID = "CID";
    public static final String ATTACHMENT_IS_INLINE = "INLINE";


    @Id
    @Column(name = ATTACHMENT_ID, nullable = false, updatable = false)
    private String attachmentId;

    @Lob
    @Column(name = ATTACHMENT_CONTENT, columnDefinition = "BLOB NOT NULL")
    private byte[] content;

    @Column(name = ATTACHMENT_TYPE)
    private String type;

    @Column(name = ATTACHMENT_OWNERS)
    @ElementCollection
    private Collection<String> owners;

    @Column(name = ATTACHMENT_MESSAGE_IDS)
    @ElementCollection
    private Collection<String> messageIds;

    @Column(name = ATTACHMENT_CID)
    private String cidValue;

    @Column(name = ATTACHMENT_IS_INLINE)
    private Boolean inline;

    public JPAAttachment(){}

    public JPAAttachment(String attachmentId, byte[] content, String type, ImmutableList<String> owners, ImmutableList<String> messageIds, String cid, Boolean inline){
        this.attachmentId = attachmentId;
        this.content = content;
        this.type = type;
        this.owners = owners;
        this.messageIds = messageIds;
        this.cidValue = cid;
        this.inline = inline;
    }


    public static JPAAttachment from(Attachment attachment) {
        return new JPAAttachment(attachment.getAttachmentId().getId(),
                attachment.getBytes(),
                attachment.getType(),
                ImmutableList.<String>builder().build(),
                ImmutableList.<String>builder().build(),
                "",
                false);
    }

    public static JPAAttachment from(MessageAttachment messageAttachment) {
        Attachment attachment = messageAttachment.getAttachment();
        return new JPAAttachment(attachment.getAttachmentId().getId(),
                attachment.getBytes(),
                attachment.getType(),
                ImmutableList.<String>builder().build(),
                ImmutableList.<String>builder().build(),
                messageAttachment.getCid().get().getValue(),
                messageAttachment.isInline());
    }

    public Attachment toAttachment() {
        return Attachment.builder()
                .attachmentId(AttachmentId.from(this.attachmentId))
                .bytes(this.content)
                .type(this.type)
                .build();

    }

    public MessageAttachment toMessageAttachment() {
        return MessageAttachment.builder()
                .attachment(this.toAttachment())
                .cid(Cid.from(this.cidValue))
                .isInline(this.inline)
                .build();
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Collection<String> getOwners() {
        return owners;
    }

    public void setOwners(ImmutableList<String> owners) {
        this.owners = owners;
    }

    public Collection<String> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(ImmutableList<String> messageId) {
        this.messageIds = messageId;
    }

    public String getCidValue() {
        return cidValue;
    }

    public void setCidValue(String cidValue) {
        this.cidValue = cidValue;
    }

    public Boolean getInline() {
        return inline;
    }

    public void setInline(Boolean inline) {
        this.inline = inline;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        JPAAttachment that = (JPAAttachment) o;
        return Objects.equals(attachmentId, that.attachmentId)
                && Arrays.equals(content, that.content)
                && Objects.equals(type, that.type)
                && this.owners.containsAll(that.owners)
                && that.owners.containsAll(this.owners)
                && this.messageIds.containsAll(that.messageIds)
                && that.messageIds.containsAll(this.messageIds)
                && Objects.equals(cidValue, that.cidValue)
                && Objects.equals(inline, that.inline);
    }


    @Override
    public int hashCode() {
        return Objects.hash(this.attachmentId, this.content, this.type, this.owners, this.messageIds, this.cidValue, this.inline);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
