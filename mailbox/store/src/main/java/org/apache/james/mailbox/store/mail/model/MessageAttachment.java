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

package org.apache.james.mailbox.store.mail.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class MessageAttachment {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Attachment attachment;
        private Optional<String> cid;
        private Boolean isInline;

        private Builder() {
            cid = Optional.absent();
        }

        public Builder attachment(Attachment attachment) {
            Preconditions.checkArgument(attachment != null);
            this.attachment = attachment;
            return this;
        }

        public Builder cid(String cid) {
            this.cid = Optional.fromNullable(cid);
            return this;
        }

        public Builder isInline(boolean isInline) {
            this.isInline = isInline;
            return this;
        }

        public MessageAttachment build() {
            Preconditions.checkState(attachment != null, "'attachment' is mandatory");
            if (isInline == null) {
                isInline = false;
            }
            if (isInline && !cid.isPresent()) {
                throw new IllegalStateException("'cid' is mandatory for inline attachments");
            }
            return new MessageAttachment(attachment, cid, isInline);
        }
    }

    private final Attachment attachment;
    private final Optional<String> cid;
    private final boolean isInline;

    @VisibleForTesting MessageAttachment(Attachment attachment, Optional<String> cid, boolean isInline) {
        this.attachment = attachment;
        this.cid = cid;
        this.isInline = isInline;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public AttachmentId getAttachmentId() {
        return attachment.getAttachmentId();
    }

    public Optional<String> getCid() {
        return cid;
    }

    public boolean isInline() {
        return isInline;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageAttachment) {
            MessageAttachment other = (MessageAttachment) obj;
            return Objects.equal(attachment, other.attachment)
                && Objects.equal(cid, other.cid)
                && Objects.equal(isInline, other.isInline);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(attachment, cid, isInline);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("attachment", attachment)
                .add("cid", cid)
                .add("isInline", isInline)
                .toString();
    }
}
