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

package org.apache.james.mailbox.cassandra.mail;

import java.util.Optional;

import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.StringBackedAttachmentId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class MessageAttachmentRepresentation {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private StringBackedAttachmentId attachmentId;
        private Optional<String> name;
        private Optional<Cid> cid;
        private Optional<Boolean> isInline;

        private Builder() {
            name = Optional.empty();
            cid = Optional.empty();
            isInline = Optional.empty();
        }

        public Builder attachmentId(StringBackedAttachmentId attachmentId) {
            Preconditions.checkArgument(attachmentId != null);
            this.attachmentId = attachmentId;
            return this;
        }

        public Builder name(String name) {
            this.name = Optional.ofNullable(name);
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

        public Builder isInline(boolean isInline) {
            this.isInline = Optional.of(isInline);
            return this;
        }

        public MessageAttachmentRepresentation build() {
            Preconditions.checkState(attachmentId != null, "'attachmentId' is mandatory");
            boolean builtIsInLine = isInline.orElse(false);
            return new MessageAttachmentRepresentation(attachmentId, name, cid, builtIsInLine);
        }
    }

    private final StringBackedAttachmentId attachmentId;
    private final Optional<String> name;
    private final Optional<Cid> cid;
    private final boolean isInline;

    @VisibleForTesting
    MessageAttachmentRepresentation(StringBackedAttachmentId attachmentId, Optional<String> name, Optional<Cid> cid, boolean isInline) {
        this.attachmentId = attachmentId;
        this.name = name;
        this.cid = cid;
        this.isInline = isInline;
    }

    public StringBackedAttachmentId getAttachmentId() {
        return attachmentId;
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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageAttachmentRepresentation) {
            MessageAttachmentRepresentation other = (MessageAttachmentRepresentation) obj;
            return Objects.equal(attachmentId, other.attachmentId)
                    && Objects.equal(name, other.name)
                    && Objects.equal(cid, other.cid)
                    && Objects.equal(isInline, other.isInline);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(attachmentId, name, cid, isInline);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("attachmentId", attachmentId)
                .add("name", name)
                .add("cid", cid)
                .add("isInline", isInline)
                .toString();
    }
}
