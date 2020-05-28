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

package org.apache.james.imap.message.request;

import java.util.Optional;
import java.util.Set;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.mailbox.model.MailboxAnnotationKey;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class GetAnnotationRequest extends AbstractImapRequest {
    public static class Builder {
        private Tag tag;
        private String mailboxName;
        private Set<MailboxAnnotationKey> keys;
        private Optional<Integer> maxsize;
        private Depth depth;

        private Builder() {
            this.depth = Depth.ZERO;
            this.maxsize = Optional.empty();
            keys = ImmutableSet.of();
        }

        public Builder tag(Tag tag) {
            this.tag = tag;
            return this;
        }

        public Builder mailboxName(String mailboxName) {
            Preconditions.checkNotNull(mailboxName);
            this.mailboxName = mailboxName;
            return this;
        }

        public Builder keys(Set<MailboxAnnotationKey> keys) {
            this.keys = ImmutableSet.copyOf(keys);
            return this;
        }

        public Builder maxsize(Optional<Integer> maxsize) {
            this.maxsize = maxsize;
            return this;
        }

        public Builder depth(Depth depth) {
            this.depth = depth;
            return this;
        }

        public GetAnnotationRequest build() {
            Preconditions.checkState(isNoDepth() || isDepthAndKeysNotEmpty());
            Preconditions.checkArgument(isNoMaxsize() || maxsize.get() > 0);
            return new GetAnnotationRequest(this);
        }

        private boolean isDepthAndKeysNotEmpty() {
            return !Depth.ZERO.equals(depth) && !keys.isEmpty();
        }

        private boolean isNoDepth() {
            return Depth.ZERO.equals(depth);
        }

        private boolean isNoMaxsize() {
            return !maxsize.isPresent();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String mailboxName;
    private final Set<MailboxAnnotationKey> keys;
    private final Optional<Integer> maxsize;
    private final Depth depth;

    private GetAnnotationRequest(Builder builder) {
        super(builder.tag, ImapConstants.GETANNOTATION_COMMAND);
        this.mailboxName = builder.mailboxName;
        this.depth = builder.depth;
        this.maxsize = builder.maxsize;
        this.keys = builder.keys;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public Set<MailboxAnnotationKey> getKeys() {
        return keys;
    }

    public Optional<Integer> getMaxsize() {
        return maxsize;
    }

    public Depth getDepth() {
        return depth;
    }

    public enum Depth {
        ZERO("0"), ONE("1"), INFINITY("infinity");

        private final String code;

        Depth(String code) {
            this.code = code;
        }

        public final String getCode() {
            return code;
        }

        public String toString() {
            return code;
        }

        public static Depth fromString(String code) {
            Preconditions.checkNotNull(code);

            for (Depth depth : Depth.values()) {
                if (code.equalsIgnoreCase(depth.code)) {
                    return depth;
                }
            }

            throw new IllegalArgumentException("Cannot lookup Depth data for: " + code);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxName", mailboxName)
            .add("keys", keys)
            .add("maxsize", maxsize)
            .add("depth", depth)
            .toString();
    }
}
