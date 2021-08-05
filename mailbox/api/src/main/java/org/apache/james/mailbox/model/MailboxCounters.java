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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class MailboxCounters {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxCounters.class);

    public interface Builder {
        @FunctionalInterface
        interface RequireMailboxId {
            RequireCount mailboxId(MailboxId mailboxId);
        }

        @FunctionalInterface
        interface RequireCount {
            RequireUnseen count(long count);
        }

        @FunctionalInterface
        interface RequireUnseen {
            FinalStage unseen(long unseen);
        }

        class FinalStage {
            private final long count;
            private final long unseen;
            private final MailboxId mailboxId;

            FinalStage(long count, long unseen, MailboxId mailboxId) {
                this.count = count;
                this.unseen = unseen;
                this.mailboxId = mailboxId;
            }

            public MailboxCounters build() {
                return new MailboxCounters(mailboxId, count, unseen);
            }
        }
    }

    public static class Sanitized extends MailboxCounters {
        static Sanitized of(MailboxId mailboxId, long count, long unseen) {
            Preconditions.checkArgument(count >= 0, "'count' need to be strictly positive");
            Preconditions.checkArgument(unseen >= 0, "'count' need to be strictly positive");
            Preconditions.checkArgument(count >= unseen, "'unseen' cannot exceed 'count'");

            return new Sanitized(mailboxId, count, unseen);
        }

        private Sanitized(MailboxId mailboxId, long count, long unseen) {
            super(mailboxId, count, unseen);
        }
    }

    public static Builder.RequireMailboxId builder() {
        return mailboxId -> count -> unseen -> new Builder.FinalStage(count, unseen, mailboxId);
    }

    public static MailboxCounters empty(MailboxId mailboxId) {
        return MailboxCounters.builder()
            .mailboxId(mailboxId)
            .count(0)
            .unseen(0)
            .build();
    }

    private final MailboxId mailboxId;
    private final long count;
    private final long unseen;

    private MailboxCounters(MailboxId mailboxId, long count, long unseen) {
        this.mailboxId = mailboxId;
        this.count = count;
        this.unseen = unseen;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    public long getCount() {
        return count;
    }

    public long getUnseen() {
        return unseen;
    }

    public MailboxCounters.Sanitized sanitize() {
        if (!isValid()) {
            LOGGER.warn("Invalid mailbox counters for {} : {} / {}", mailboxId, unseen, count);
        }
        long sanitizedCount = Math.max(count, 0);
        long positiveUnseen = Math.max(unseen, 0);
        long sanitizedUnseen = Math.min(positiveUnseen, sanitizedCount);

        return Sanitized.of(mailboxId, sanitizedCount, sanitizedUnseen);
    }

    public boolean isValid() {
        return count >= 0
            && unseen >= 0
            && count >= unseen;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxCounters) {
            MailboxCounters that = (MailboxCounters) o;

            return Objects.equal(this.count, that.count)
                && Objects.equal(this.unseen, that.unseen)
                && Objects.equal(this.mailboxId, that.mailboxId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(count, unseen, mailboxId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxId", mailboxId)
            .add("count", count)
            .add("unseen", unseen)
            .toString();
    }
}
