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

import javax.mail.Flags;

import org.apache.james.mailbox.ModSeq;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class ComposedMessageIdWithMetaData {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ComposedMessageId composedMessageId;
        private Flags flags;
        private ModSeq modSeq;

        private Builder() {
        }

        public Builder composedMessageId(ComposedMessageId composedMessageId) {
            this.composedMessageId = composedMessageId;
            return this;
        }

        public Builder flags(Flags flags) {
            this.flags = flags;
            return this;
        }

        public Builder modSeq(ModSeq modSeq) {
            this.modSeq = modSeq;
            return this;
        }

        public ComposedMessageIdWithMetaData build() {
            Preconditions.checkNotNull(composedMessageId, "'composedMessageId' is mandatory");
            Preconditions.checkNotNull(flags, "'flags' is mandatory");
            Preconditions.checkNotNull(modSeq, "'modSeq' is mandatory");
            return new ComposedMessageIdWithMetaData(composedMessageId, flags, modSeq);
        }
    }

    private final ComposedMessageId composedMessageId;
    private final Flags flags;
    private final ModSeq modSeq;

    public ComposedMessageIdWithMetaData(ComposedMessageId composedMessageId, Flags flags, ModSeq modSeq) {
        this.composedMessageId = composedMessageId;
        this.flags = flags;
        this.modSeq = modSeq;
    }

    public ComposedMessageId getComposedMessageId() {
        return composedMessageId;
    }

    public Flags getFlags() {
        return flags;
    }

    public ModSeq getModSeq() {
        return modSeq;
    }

    public boolean isMatching(MessageId messageId) {
        return getComposedMessageId().getMessageId().equals(messageId);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ComposedMessageIdWithMetaData) {
            ComposedMessageIdWithMetaData other = (ComposedMessageIdWithMetaData) o;
            return Objects.equal(composedMessageId, other.composedMessageId)
                && Objects.equal(flags, other.flags)
                && Objects.equal(modSeq, other.modSeq);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(composedMessageId, flags, modSeq);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("composedMessageId", composedMessageId)
            .add("flags", flags)
            .add("modSeq", modSeq)
            .toString();
    }
}
