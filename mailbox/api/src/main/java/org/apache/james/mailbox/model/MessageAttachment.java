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

package org.apache.james.mailbox.model;

import java.util.List;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class MessageAttachment {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Attachment attachment;
        private Optional<String> name;
        private Optional<Cid> cid;
        private Optional<Boolean> isInline = Optional.empty();

        private Builder() {
            name = Optional.empty();
            cid = Optional.empty();
        }

        public Builder attachment(Attachment attachment) {
            Preconditions.checkArgument(attachment != null);
            this.attachment = attachment;
            return this;
        }

        public Builder name(String name) {
            this.name = Optional.ofNullable(name);
            return this;
        }

        public Builder name(Optional<String> name) {
            this.name = name;
            return this;
        }

        public Builder cid(Optional<Cid> cid) {
            Preconditions.checkNotNull(cid);
            this.cid = cid;
            return this;
        }

        
        public Builder cid(Cid cid) {
            this.cid = Optional.ofNullable(cid);
            return this;
        }

        public Builder isInline(Boolean isInline) {
            this.isInline = Optional.ofNullable(isInline);
            return this;
        }

        public MessageAttachment build() {
            Preconditions.checkState(attachment != null, "'attachment' is mandatory");
            return new MessageAttachment(attachment, name, cid, isInline.orElse(false));
        }
    }

    public static boolean hasNonInlinedAttachment(List<MessageAttachment> attachments) {
        return attachments.stream()
            .anyMatch(messageAttachment -> !messageAttachment.isInlinedWithCid());
    }

    private final Attachment attachment;
    private final Optional<String> name;
    private final Optional<Cid> cid;
    private final boolean isInline;

    public MessageAttachment(Attachment attachment, Optional<String> name, Optional<Cid> cid, boolean isInline) {
        this.attachment = attachment;
        this.name = name;
        this.cid = cid;
        this.isInline = isInline;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public AttachmentId getAttachmentId() {
        return attachment.getAttachmentId();
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<Cid> getCid() {
        return cid;
    }

    public boolean isInline() {
        return isInline;
    }

    public boolean isInlinedWithCid() {
        return isInline && cid.isPresent();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageAttachment) {
            MessageAttachment other = (MessageAttachment) obj;
            return Objects.equal(attachment, other.attachment)
                && Objects.equal(name, other.name)
                && Objects.equal(cid, other.cid)
                && Objects.equal(isInline, other.isInline);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(attachment, name, cid, isInline);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("attachment", attachment)
                .add("name", name)
                .add("cid", cid)
                .add("isInline", isInline)
                .toString();
    }
}
